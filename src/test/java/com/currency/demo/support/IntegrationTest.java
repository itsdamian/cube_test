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
    /** Same version line as the kafka-clients jar (3.9.2). Single KRaft broker, no ZooKeeper. */
    public static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("apache/kafka:3.9.2");

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            // Many cached test contexts (each with a small pool) share this one database.
            .withCommand("postgres", "-c", "max_connections=300");

    @ServiceConnection
    protected static final KafkaContainer KAFKA = new KafkaContainer(KAFKA_IMAGE);

    static {
        POSTGRES.start();
        KAFKA.start();
    }
}
