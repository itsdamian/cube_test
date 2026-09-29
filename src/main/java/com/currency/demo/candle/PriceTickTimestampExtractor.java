package com.currency.demo.candle;

import com.currency.demo.pricing.PriceTick;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;

/**
 * Event time for Kafka Streams = when the trade happened at the exchange ({@code eventTime}),
 * not when Kafka received the record. Windows (candles) are therefore correct even when
 * records arrive late or out of order.
 */
public class PriceTickTimestampExtractor implements TimestampExtractor {

    @Override
    public long extract(ConsumerRecord<Object, Object> record, long partitionTime) {
        if (record.value() instanceof PriceTick tick) {
            return tick.eventTime().toEpochMilli();
        }
        return record.timestamp(); // not a tick (should not happen): fall back to Kafka's timestamp
    }
}
