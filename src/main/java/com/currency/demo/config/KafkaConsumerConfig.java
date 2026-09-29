package com.currency.demo.config;

import com.currency.demo.pricing.PriceTick;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Typed listener container factories: one per event type, all JSON.
 *
 * <p>Each factory starts from the configuration of Spring Boot's own consumer factory, which
 * already contains the effective bootstrap servers (including a Testcontainers
 * {@code @ServiceConnection}), and swaps in a JSON deserializer for the target type.
 * {@link ErrorHandlingDeserializer} turns an unreadable record into a {@code null} value
 * instead of an exception that would make the container retry the same "poison pill" forever.
 */
@Configuration(proxyBeanMethods = false)
public class KafkaConsumerConfig {

    /** Batch listener for {@link PriceTick}s (used by the DB persister). */
    @Bean
    ConcurrentKafkaListenerContainerFactory<String, PriceTick> tickBatchListenerFactory(
            ConsumerFactory<?, ?> bootConsumerFactory, ObjectMapper objectMapper) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, PriceTick>();
        factory.setConsumerFactory(jsonConsumerFactory(bootConsumerFactory, objectMapper, PriceTick.class));
        factory.setBatchListener(true);
        return factory;
    }

    static <T> ConsumerFactory<String, T> jsonConsumerFactory(ConsumerFactory<?, ?> bootConsumerFactory,
                                                             ObjectMapper objectMapper, Class<T> type) {
        Map<String, Object> config = new HashMap<>(bootConsumerFactory.getConfigurationProperties());
        JsonDeserializer<T> json = new JsonDeserializer<>(type, objectMapper, false);
        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), new ErrorHandlingDeserializer<>(json));
    }
}
