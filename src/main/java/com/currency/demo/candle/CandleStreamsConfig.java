package com.currency.demo.candle;

import com.currency.demo.config.AppProperties;
import com.currency.demo.pricing.PriceTick;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.streams.StreamsBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.support.serializer.JsonSerde;

/**
 * Starts the Kafka Streams application (only when {@code app.streams.enabled=true}).
 * Spring Boot builds the {@code StreamsBuilder} from {@code spring.kafka.streams.*}
 * (application id, state dir, error handling); this class only adds the topology.
 */
@Configuration(proxyBeanMethods = false)
@EnableKafkaStreams
@ConditionalOnProperty(name = "app.streams.enabled", havingValue = "true")
public class CandleStreamsConfig {

    /** Adds the candle topology to the StreamsBuilder that Spring Kafka manages and starts. */
    @Autowired
    void candleTopology(StreamsBuilder builder, ObjectMapper objectMapper, AppProperties props) {
        CandleTopology.build(builder, json(PriceTick.class, objectMapper), json(CandleAccumulator.class, objectMapper),
                json(Candle.class, objectMapper), props.streams().grace());
    }

    /**
     * If a stream thread dies from an unexpected exception (e.g. after a long broker outage), start a
     * fresh thread instead of leaving the whole application in ERROR until someone restarts the pod.
     * (Unreadable records never get here: LogAndContinueExceptionHandler skips them.)
     */
    @Bean   // static: the StreamsBuilderFactoryBean needs it before this configuration instance exists
    static StreamsBuilderFactoryBeanConfigurer replaceDeadStreamThreads() {
        return factoryBean -> factoryBean.setStreamsUncaughtExceptionHandler(exception -> {
            LoggerFactory.getLogger(CandleStreamsConfig.class).error("Stream thread died; replacing it", exception);
            return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD;
        });
    }

    /** JSON serde with Boot's ObjectMapper and no Java type headers (same format as the producers). */
    public static <T> Serde<T> json(Class<T> type, ObjectMapper objectMapper) {
        return new JsonSerde<>(type, objectMapper).noTypeInfo().ignoreTypeHeaders();
    }
}
