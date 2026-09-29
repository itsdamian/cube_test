package com.currency.demo.feed;

import com.currency.demo.config.AppProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;

/** Beans for the price-feed side (ingest). More are added as the feed clients arrive. */
@Configuration(proxyBeanMethods = false)
public class FeedConfig {

    @Bean
    TickPublisher tickPublisher(KafkaOperations<String, Object> kafka, AppProperties props, MeterRegistry registry) {
        return new TickPublisher(kafka, props.feed().publishQueueCapacity(), registry);
    }
}
