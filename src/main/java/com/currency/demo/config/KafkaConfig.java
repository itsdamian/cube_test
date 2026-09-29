package com.currency.demo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Kafka wiring shared by the whole app.
 *
 * <p>Topics are declared as {@link NewTopic} beans; Spring's {@code KafkaAdmin} creates
 * any that are missing at startup (it never deletes or shrinks existing ones).
 *
 * <p>Event values are JSON written with Spring Boot's own {@link ObjectMapper}, so
 * {@code Instant}s are ISO-8601 strings and {@code BigDecimal}s keep their exact digits.
 * Type-info headers are turned off: every topic carries exactly one event type and each
 * consumer names the class it expects, which keeps topics readable by non-Java clients.
 */
@Configuration(proxyBeanMethods = false)
public class KafkaConfig {

    @Bean
    @SuppressWarnings("unchecked")
    DefaultKafkaProducerFactoryCustomizer jsonValueSerializer(ObjectMapper objectMapper) {
        return factory -> {
            JsonSerializer<Object> serializer = new JsonSerializer<>(objectMapper);
            serializer.setAddTypeInfo(false);
            // Boot hands us a DefaultKafkaProducerFactory<?, ?>; our values are always serialised as JSON Objects.
            ((DefaultKafkaProducerFactory<Object, Object>) factory).setValueSerializerSupplier(() -> serializer);
        };
    }

    @Bean
    NewTopic priceTicksTopic(AppProperties props) {
        return TopicBuilder.name(Topics.PRICE_TICKS)
                .partitions(props.kafka().tickPartitions())
                .replicas(props.kafka().replicationFactor())
                .build();
    }

    @Bean
    NewTopic candlesTopic(AppProperties props) {
        return TopicBuilder.name(Topics.CANDLES).partitions(1).replicas(props.kafka().replicationFactor()).build();
    }

    @Bean
    NewTopic alertsTriggeredTopic(AppProperties props) {
        return TopicBuilder.name(Topics.ALERTS_TRIGGERED).partitions(1).replicas(props.kafka().replicationFactor()).build();
    }

    @Bean
    NewTopic feedStatusTopic(AppProperties props) {
        return TopicBuilder.name(Topics.FEED_STATUS)
                .partitions(1)
                .replicas(props.kafka().replicationFactor())
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
                .build();
    }
}
