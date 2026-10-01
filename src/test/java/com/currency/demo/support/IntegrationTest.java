package com.currency.demo.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need real infrastructure: PostgreSQL and a Kafka broker.
 *
 * <p>Uses the "singleton container" pattern: the container is a static field started
 * once per test JVM and shared by every test class, instead of one container per class.
 * Testcontainers' Ryuk sidecar removes it when the JVM exits.
 * {@code @ServiceConnection} lets Spring Boot read the container's JDBC URL, user and
 * password and configure the DataSource - no hand-written properties.
 *
 * <p>Image tags are pinned (never {@code latest}) so tests are reproducible and the
 * images can be pre-pulled for offline runs (AC13).
 */
public abstract class IntegrationTest {

    public static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:17.11-alpine");
    /**
     * Same broker version as docker compose and the Kubernetes cluster (Strimzi 1.2 supports Kafka
     * 4.2 / 4.3 only), so tests run against what is deployed. The kafka-clients jar stays on the
     * version Spring Boot manages (3.9.x): Kafka 4 brokers accept clients from 2.1 on.
     * Single KRaft broker, no ZooKeeper.
     */
    public static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("apache/kafka:4.3.1");

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            // Many cached test contexts (each with a small pool) share this one database.
            .withCommand("postgres", "-c", "max_connections=300");

    @ServiceConnection
    protected static final KafkaContainer KAFKA = new KafkaContainer(KAFKA_IMAGE)
            // Kafka 4 (KIP-1030) rejects records timestamped more than 1 hour in the future
            // (log.message.timestamp.after.max.ms). Some pipeline tests deliberately use future
            // trade times (2032, 2035) so their candles never overlap other tests' data in this
            // shared broker; lift the limit for tests only. Compose and the cluster keep Kafka's
            // default, which protects real data against producers with a skewed clock.
            .withEnv("KAFKA_LOG_MESSAGE_TIMESTAMP_AFTER_MAX_MS", String.valueOf(Long.MAX_VALUE));

    static {
        POSTGRES.start();
        KAFKA.start();
    }
}
