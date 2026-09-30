package com.currency.demo.config;

import com.currency.demo.support.IntegrationTest;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.BatchMessageListener;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * QA CONCERN 13: listeners that write to the database must survive a long database outage.
 *
 * <p>A real listener container on a real broker; the listener throws a database-down exception
 * for its first 15 attempts, then succeeds. With our policy (unlimited exponential retries) the
 * record is eventually written. Spring Kafka's default policy - 9 immediate retries, then skip
 * and commit - is run side by side to show the data it would lose.
 */
class PersistingErrorHandlerTest extends IntegrationTest {

    private static final int FAILURES_BEFORE_DB_IS_BACK = 15;

    @Test
    void ourPolicyKeepsRetryingUntilTheWriteSucceeds() {
        ExponentialBackOff fast = new ExponentialBackOff(10, 2.0);   // same shape as production, ms not s
        fast.setMaxInterval(50);
        fast.setMaxElapsedTime(Long.MAX_VALUE);

        Outcome outcome = run(fast);

        await().atMost(Duration.ofSeconds(30)).until(() -> !outcome.written.isEmpty());
        assertThat(outcome.written).containsExactly("tick-1");
        assertThat(outcome.attempts.get()).isEqualTo(FAILURES_BEFORE_DB_IS_BACK + 1);
        outcome.container.stop();
    }

    @Test
    void springDefaultPolicyWouldSkipTheRecordAndLoseIt() throws Exception {
        Outcome outcome = run(new FixedBackOff(0, 9));

        await().atMost(Duration.ofSeconds(30)).until(() -> outcome.attempts.get() >= 10);
        Thread.sleep(1_000);                       // give it every chance to try again
        assertThat(outcome.attempts.get()).as("1 try + 9 retries, then skipped").isEqualTo(10);
        assertThat(outcome.written).as("the tick was never stored").isEmpty();
        outcome.container.stop();
    }

    @Test
    void productionBackOffIsUnlimitedAndCappedAt30Seconds() {
        var execution = KafkaConsumerConfig.databaseWriteBackOff().start();
        long last = 0;
        for (int i = 0; i < 1_000; i++) {
            last = execution.nextBackOff();
            assertThat(last).as("attempt %d must not give up", i).isNotEqualTo(org.springframework.util.backoff.BackOffExecution.STOP);
        }
        assertThat(last).isEqualTo(30_000);
    }

    private record Outcome(ConcurrentMessageListenerContainer<String, String> container,
                           AtomicInteger attempts, List<String> written) {
    }

    private Outcome run(BackOff backOff) {
        String topic = "error-policy-" + UUID.randomUUID();
        try (AdminClient admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        var consumerFactory = new DefaultKafkaConsumerFactory<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, topic,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer());
        var factory = KafkaConsumerConfig.persistingBatchFactory(consumerFactory, backOff);
        ConcurrentMessageListenerContainer<String, String> container = factory.createContainer(topic);

        AtomicInteger attempts = new AtomicInteger();
        List<String> written = new CopyOnWriteArrayList<>();
        container.setupMessageListener((BatchMessageListener<String, String>) records -> {
            if (attempts.incrementAndGet() <= FAILURES_BEFORE_DB_IS_BACK) {
                throw new DataAccessResourceFailureException("database is down");
            }
            records.forEach(r -> written.add(r.value()));
        });
        container.start();

        try (var producer = new KafkaProducer<>(Map.<String, Object>of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()),
                new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(topic, "BTC-USD", "tick-1"));
        }
        return new Outcome(container, attempts, written);
    }
}
