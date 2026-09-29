package com.currency.demo.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need real infrastructure (PostgreSQL now, Kafka from task 4).
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

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE);

    static {
        POSTGRES.start();
    }
}
