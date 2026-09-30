package com.currency.demo.health;

import org.apache.kafka.streams.KafkaStreams;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Component;

/**
 * Health component {@code kafkaStreams}: UP only while the candle topology is RUNNING or
 * REBALANCING. If a stream thread dies (ERROR) candles would silently stop; reporting DOWN makes
 * that visible and takes the instance out of service. Exists only when Streams is enabled.
 */
@Component
@ConditionalOnProperty(name = "app.streams.enabled", havingValue = "true")
public class KafkaStreamsHealthIndicator implements HealthIndicator {

    private final StreamsBuilderFactoryBean streams;

    public KafkaStreamsHealthIndicator(StreamsBuilderFactoryBean streams) {
        this.streams = streams;
    }

    @Override
    public Health health() {
        KafkaStreams kafkaStreams = streams.getKafkaStreams();
        if (kafkaStreams == null) {
            return Health.down().withDetail("state", "not started").build();
        }
        KafkaStreams.State state = kafkaStreams.state();
        boolean healthy = state == KafkaStreams.State.RUNNING || state == KafkaStreams.State.REBALANCING;
        return (healthy ? Health.up() : Health.down()).withDetail("state", state.name()).build();
    }
}
