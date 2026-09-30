package com.currency.demo.candle;

import com.currency.demo.config.Topics;
import com.currency.demo.pricing.PriceTick;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KGroupedStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.TimeWindows;

import java.time.Duration;
import java.time.Instant;

/**
 * Kafka Streams topology: {@code btc.price.ticks} -> 1-minute and 5-minute OHLC candles -> {@code btc.candles}.
 *
 * <ul>
 *   <li><b>Event time</b>: {@link PriceTickTimestampExtractor} places each tick by its exchange time.</li>
 *   <li><b>Tumbling windows</b> [start, end): a tick at exactly 12:01:00.000 belongs to the 12:01 candle.</li>
 *   <li><b>Grace</b>: a window still accepts late ticks for {@code grace} after it ends; later ones are
 *       dropped (counted in the built-in {@code dropped-records} metric).</li>
 *   <li><b>suppress(untilWindowCloses)</b>: emit each candle once, when final, instead of an update per tick.
 *       Windows close as <em>stream time</em> (the highest event time seen) advances, i.e. when newer ticks arrive.</li>
 * </ul>
 * Pure function of its inputs, so it is tested with {@code TopologyTestDriver} (no broker).
 */
public final class CandleTopology {

    public record Interval(String name, Duration size) {
    }

    public static final Interval ONE_MINUTE = new Interval("1m", Duration.ofMinutes(1));
    public static final Interval FIVE_MINUTES = new Interval("5m", Duration.ofMinutes(5));

    private CandleTopology() {
    }

    public static void build(StreamsBuilder builder, Serde<PriceTick> tickSerde,
                             Serde<CandleAccumulator> accumulatorSerde, Serde<Candle> candleSerde, Duration grace) {
        KGroupedStream<String, PriceTick> ticksByPair = builder
                .stream(Topics.PRICE_TICKS, Consumed.with(Serdes.String(), tickSerde)
                        .withTimestampExtractor(new PriceTickTimestampExtractor()))
                .filter((pair, tick) -> tick != null)
                .groupByKey(Grouped.with(Serdes.String(), tickSerde));

        for (Interval interval : new Interval[]{ONE_MINUTE, FIVE_MINUTES}) {
            ticksByPair
                    .windowedBy(TimeWindows.ofSizeAndGrace(interval.size(), grace))
                    .aggregate(CandleAccumulator::empty, (pair, tick, acc) -> acc.add(tick),
                            Materialized.<String, CandleAccumulator, org.apache.kafka.streams.state.WindowStore<
                                            org.apache.kafka.common.utils.Bytes, byte[]>>as("candles-" + interval.name())
                                    .withKeySerde(Serdes.String())
                                    .withValueSerde(accumulatorSerde))
                    .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))
                    .toStream()
                    .map((window, acc) -> {
                        Candle candle = acc.toCandle(window.key(), interval.name(),
                                Instant.ofEpochMilli(window.window().start()),
                                Instant.ofEpochMilli(window.window().end()));
                        return KeyValue.pair(candle.key(), candle);
                    })
                    .to(Topics.CANDLES, Produced.with(Serdes.String(), candleSerde));
        }
    }
}
