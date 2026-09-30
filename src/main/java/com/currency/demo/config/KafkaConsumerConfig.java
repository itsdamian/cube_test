package com.currency.demo.config;

import com.currency.demo.alert.AlertDtos.AlertTriggered;
import com.currency.demo.candle.Candle;
import com.currency.demo.feed.FeedStatus;
import com.currency.demo.pricing.PriceTick;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;

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

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    /** Batch listener for {@link PriceTick}s (used by the DB persister). */
    @Bean
    ConcurrentKafkaListenerContainerFactory<String, PriceTick> tickBatchListenerFactory(
            ConsumerFactory<?, ?> bootConsumerFactory, ObjectMapper objectMapper) {
        return persistingBatchFactory(jsonConsumerFactory(bootConsumerFactory, objectMapper, PriceTick.class));
    }

    /** Batch listener for {@link Candle}s (used by the DB persister). */
    @Bean
    ConcurrentKafkaListenerContainerFactory<String, Candle> candleBatchListenerFactory(
            ConsumerFactory<?, ?> bootConsumerFactory, ObjectMapper objectMapper) {
        return persistingBatchFactory(jsonConsumerFactory(bootConsumerFactory, objectMapper, Candle.class));
    }

    /**
     * Listeners that write to the database must not lose data when the database is down.
     * Spring Kafka's default error handler retries a failed batch only 9 times in quick
     * succession and then SKIPS it (offsets committed -> rows lost, while candles keep being
     * produced from the same ticks). Instead we retry with exponential backoff (1 s doubling to
     * 30 s) for as long as it takes; consumption simply pauses until the database is back.
     * Unreadable records never reach this point: ErrorHandlingDeserializer turns them into nulls.
     */
    static <T> ConcurrentKafkaListenerContainerFactory<String, T> persistingBatchFactory(
            ConsumerFactory<String, T> consumerFactory) {
        return persistingBatchFactory(consumerFactory, databaseWriteBackOff());
    }

    /** Exponential 1 s, 2 s, 4 s ... capped at 30 s, with no limit on the number of attempts. */
    public static BackOff databaseWriteBackOff() {
        ExponentialBackOff backOff = new ExponentialBackOff(1_000, 2.0);
        backOff.setMaxInterval(30_000);
        backOff.setMaxElapsedTime(Long.MAX_VALUE); // retry forever
        return backOff;
    }

    /** Package-visible with an explicit BackOff so tests can use millisecond delays. */
    public static <T> ConcurrentKafkaListenerContainerFactory<String, T> persistingBatchFactory(
            ConsumerFactory<String, T> consumerFactory, BackOff backOff) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, T>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        factory.setCommonErrorHandler(persistingErrorHandler(backOff));
        return factory;
    }

    /**
     * Retry (transient) failures forever, but NOT errors that can never succeed: a row the database
     * rejects (constraint/overflow = {@link DataIntegrityViolationException}) would otherwise block
     * the partition for good. Those records are logged at ERROR and skipped. Every retry is logged
     * at WARN so an outage is visible. (The persisters additionally isolate a bad row so the good
     * rows of the same batch are still written - this is the second line of defence.)
     */
    static DefaultErrorHandler persistingErrorHandler(BackOff backOff) {
        DefaultErrorHandler handler = new DefaultErrorHandler((record, ex) -> log.error(
                "Skipping record {}-{}@{} that can never be stored: {}", record.topic(), record.partition(),
                record.offset(), ex.toString()), backOff);
        handler.addNotRetryableExceptions(DataIntegrityViolationException.class);
        handler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int attempt) {
                log.warn("Write to database failed (attempt {}), will retry: {}", attempt, rootCause(ex));
            }

            @Override
            public void failedDelivery(ConsumerRecords<?, ?> records, Exception ex, int attempt) {
                log.warn("Writing a batch of {} record(s) to the database failed (attempt {}), will retry: {}",
                        records.count(), attempt, rootCause(ex));
            }
        });
        return handler;
    }

    private static String rootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.toString();
    }

    /** Single-record listener for {@link FeedStatus} (status tracker, SSE). */
    @Bean
    ConcurrentKafkaListenerContainerFactory<String, FeedStatus> feedStatusListenerFactory(
            ConsumerFactory<?, ?> bootConsumerFactory, ObjectMapper objectMapper) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, FeedStatus>();
        factory.setConsumerFactory(jsonConsumerFactory(bootConsumerFactory, objectMapper, FeedStatus.class));
        return factory;
    }

    /** Single-record listener for {@link AlertTriggered} events (SSE). */
    @Bean
    ConcurrentKafkaListenerContainerFactory<String, AlertTriggered> alertTriggeredListenerFactory(
            ConsumerFactory<?, ?> bootConsumerFactory, ObjectMapper objectMapper) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, AlertTriggered>();
        factory.setConsumerFactory(jsonConsumerFactory(bootConsumerFactory, objectMapper, AlertTriggered.class));
        return factory;
    }

    /** Batch listener for ticks that only need the newest value (SSE); no DB, so default error handling. */
    @Bean
    ConcurrentKafkaListenerContainerFactory<String, PriceTick> tickLatestListenerFactory(
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
