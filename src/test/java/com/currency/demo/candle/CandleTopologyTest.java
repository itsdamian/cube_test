package com.currency.demo.candle;

import com.currency.demo.candle.CandleTopology.Interval;
import com.currency.demo.candle.CandleStreamsConfig;
import com.currency.demo.config.Topics;
import com.currency.demo.pricing.PriceTick;
import com.currency.demo.pricing.TickOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The candle topology on {@link TopologyTestDriver}: real Kafka Streams processing, no broker.
 * Every input record gets a Kafka timestamp one hour AWAY from its payload eventTime, so correct
 * candles prove the event-time extractor is used.
 */
class CandleTopologyTest {

    private static final Instant T0 = Instant.parse("2026-09-29T12:00:00Z");
    private static final Duration GRACE = Duration.ofSeconds(5);

    private final ObjectMapper json = JsonMapper.builder().findAndAddModules().build();
    private TopologyTestDriver driver;
    private TestInputTopic<String, PriceTick> ticks;
    private TestInputTopic<String, byte[]> rawTicks;
    private TestOutputTopic<String, Candle> candles;
    private final List<PriceTick> accepted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Serde<PriceTick> tickSerde = CandleStreamsConfig.json(PriceTick.class, json);
        Serde<Candle> candleSerde = CandleStreamsConfig.json(Candle.class, json);
        StreamsBuilder builder = new StreamsBuilder();
        CandleTopology.build(builder, tickSerde, CandleStreamsConfig.json(CandleAccumulator.class, json), candleSerde, GRACE);

        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "candle-test");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        config.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                LogAndContinueExceptionHandler.class);
        driver = new TopologyTestDriver(builder.build(), config);
        ticks = driver.createInputTopic(Topics.PRICE_TICKS, new StringSerializer(), tickSerde.serializer());
        rawTicks = driver.createInputTopic(Topics.PRICE_TICKS, new StringSerializer(), new ByteArraySerializer());
        candles = driver.createOutputTopic(Topics.CANDLES, new StringDeserializer(), candleSerde.deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    /** Pipe a tick whose Kafka record timestamp is deliberately one hour off its event time. */
    private PriceTick send(Instant eventTime, String price, Instant receivedAt, UUID id) {
        PriceTick tick = new PriceTick(id, PriceTick.BTC_USD, new BigDecimal(price), "coinbase", eventTime, receivedAt);
        ticks.pipeInput(PriceTick.BTC_USD, tick, eventTime.plus(Duration.ofHours(1)));
        return tick;
    }

    private PriceTick send(Instant eventTime, String price) {
        return send(eventTime, price, eventTime.plusMillis(30), UUID.randomUUID());
    }

    @Test
    void twelveMinutesOfTicksProduceFinalCandlesMatchingAHandComputedAggregate() {
        Random random = new Random(42);
        PriceTick boundary = null;
        // One tick every 5 s from 12:00:00 to 12:11:55, prices wandering around 84,000.
        for (int s = 0; s < 12 * 60; s += 5) {
            String price = BigDecimal.valueOf(random.nextInt(2_000), 2).add(new BigDecimal("83000")).toPlainString();
            PriceTick t = send(T0.plusSeconds(s), price);
            accepted.add(t);
            if (s == 5 * 60) {
                boundary = t;       // exactly 12:05:00.000 -> belongs to the 12:05 candles ([start, end))
            }
            if (s == 3 * 60 + 30) {
                // Two more ticks with the SAME eventTime (Kraken batches trades like this):
                // order is decided by receivedAt, then by event id compared as unsigned bytes.
                Instant tie = T0.plusSeconds(s);
                accepted.add(send(tie, "81111.11", tie.plusMillis(1), UUID.fromString("ffffffff-0000-0000-0000-000000000000")));
                accepted.add(send(tie, "82222.22", tie.plusMillis(1), UUID.fromString("00000000-0000-0000-0000-000000000001")));
            }
            if (s == 11 * 60 + 55) {
                // Out of order but within grace: arrives after 12:11:55, belongs to 12:11:52.
                accepted.add(send(T0.plusSeconds(11 * 60 + 52), "85999.99"));
            }
        }
        // Advance stream time to 12:13:00 so every 1m window up to 12:11 and 5m up to 12:05 closes.
        send(T0.plusSeconds(13 * 60), "84000.00");

        Map<String, Candle> out = candles.readValuesToList().stream()
                .collect(Collectors.toMap(c -> c.interval() + "@" + c.openTime(), c -> c, (a, b) -> b, TreeMap::new));

        List<Candle> oneMinute = out.values().stream().filter(c -> c.interval().equals("1m")).toList();
        List<Candle> fiveMinute = out.values().stream().filter(c -> c.interval().equals("5m")).toList();
        assertThat(oneMinute).hasSize(12);   // 12:00 .. 12:11 closed; 12:12 is still open
        assertThat(fiveMinute).hasSize(2);   // 12:00, 12:05 closed; 12:10 still open
        for (Candle c : out.values()) {
            assertThat(c).usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected(c.interval().equals("1m") ? CandleTopology.ONE_MINUTE : CandleTopology.FIVE_MINUTES,
                            c.openTime()));
        }
        // The 12:05:00.000 tick opens the 12:05 candles and is not part of 12:04.
        assertThat(out.get("1m@" + T0.plusSeconds(300)).open()).isEqualByComparingTo(boundary.price());
        assertThat(out.get("5m@" + T0.plusSeconds(300)).open()).isEqualByComparingTo(boundary.price());
        assertThat(out.get("1m@" + T0.plusSeconds(240)).tickCount()).isEqualTo(12);
        // The 12:03 candle contains the two tie ticks (12 regular + 2).
        assertThat(out.get("1m@" + T0.plusSeconds(180)).tickCount()).isEqualTo(14);
        assertThat(out.get("1m@" + T0.plusSeconds(180)).low()).isEqualByComparingTo("81111.11");
    }

    @Test
    void sameEventTimeTieIsBrokenByReceivedAtThenUnsignedEventId() {
        Instant t = T0.plusSeconds(10);
        // Same eventTime and receivedAt. As UNSIGNED bytes (PostgreSQL), 0x00.. < 0x7f.. < 0xff..;
        // Java's signed UUID.compareTo would put 0xff.. first.
        send(t, "100.00", t, UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
        send(t, "200.00", t, UUID.fromString("00000000-0000-0000-0000-000000000000"));
        send(t, "300.00", t, UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff"));
        send(T0.plusSeconds(120), "1.00");   // close the 12:00 window

        Candle c = candles.readValuesToList().stream().filter(x -> x.interval().equals("1m")).findFirst().orElseThrow();
        assertThat(c.open()).isEqualByComparingTo("200.00");
        assertThat(c.close()).isEqualByComparingTo("100.00");
        assertThat(c.tickCount()).isEqualTo(3);
    }

    @Test
    void tickLaterThanGraceIsDroppedAndCounted() {
        send(T0.plusSeconds(10), "100.00");
        send(T0.plusSeconds(20), "110.00");
        // Stream time moves to 12:01:06 -> the 12:00 1m window (end 12:01 + 5 s grace) is closed.
        send(T0.plusSeconds(66), "120.00");
        send(T0.plusSeconds(30), "999999.00");            // late: must NOT change the 12:00 candle
        send(T0.plusSeconds(200), "130.00");              // close more windows

        Candle first = candles.readValuesToList().stream()
                .filter(x -> x.interval().equals("1m") && x.openTime().equals(T0)).findFirst().orElseThrow();
        assertThat(first.high()).isEqualByComparingTo("110.00");
        assertThat(first.tickCount()).isEqualTo(2);
        double dropped = driver.metrics().entrySet().stream()
                .filter(e -> e.getKey().name().equals("dropped-records-total"))
                .mapToDouble(e -> ((Number) e.getValue().metricValue()).doubleValue()).sum();
        assertThat(dropped).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void candleIsEmittedOnlyOnceWhenItsWindowClosesPlusGrace() {
        send(T0.plusSeconds(10), "100.00");
        send(T0.plusSeconds(64), "101.00");               // 12:01:04 < 12:01:05 -> 12:00 not closed yet
        assertThat(candles.readValuesToList()).isEmpty();

        send(T0.plusSeconds(65), "102.00");               // exactly end + grace -> closed
        List<Candle> emitted = candles.readValuesToList();
        assertThat(emitted).extracting(Candle::interval, Candle::openTime).containsExactly(
                org.assertj.core.groups.Tuple.tuple("1m", T0));
        assertThat(emitted.getFirst().key()).isEqualTo("BTC-USD|1m|" + T0);
    }

    @Test
    void unreadableRecordIsSkippedAndProcessingContinues() {
        rawTicks.pipeInput(PriceTick.BTC_USD, "definitely not json".getBytes(StandardCharsets.UTF_8));
        send(T0.plusSeconds(1), "100.00");
        rawTicks.pipeInput(PriceTick.BTC_USD, "{\"pair\":".getBytes(StandardCharsets.UTF_8));
        send(T0.plusSeconds(2), "105.00");
        send(T0.plusSeconds(70), "1.00");

        Candle c = candles.readValuesToList().getFirst();
        assertThat(c.tickCount()).isEqualTo(2);
        assertThat(c.high()).isEqualByComparingTo("105.00");
    }

    /** Hand-computed candle over the accepted ticks, using the same ordering rule as the database. */
    private Candle expected(Interval interval, Instant open) {
        Instant close = open.plus(interval.size());
        List<PriceTick> in = accepted.stream()
                .filter(t -> !t.eventTime().isBefore(open) && t.eventTime().isBefore(close))
                .sorted(TickOrder.COMPARATOR).toList();
        return new Candle(PriceTick.BTC_USD, interval.name(), open, close,
                in.getFirst().price(),
                in.stream().map(PriceTick::price).max(BigDecimal::compareTo).orElseThrow(),
                in.stream().map(PriceTick::price).min(BigDecimal::compareTo).orElseThrow(),
                in.getLast().price(), in.size());
    }
}
