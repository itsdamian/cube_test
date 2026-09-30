package com.currency.demo.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Health component {@code kafka}: UP if the broker answers "describe cluster" within 3 seconds.
 * Spring Boot has no built-in Kafka indicator. Part of the readiness group only - an unreachable
 * broker should take the pod out of the load balancer, not restart it.
 */
@Component
public class KafkaHealthIndicator implements HealthIndicator, DisposableBean {

    static final int TIMEOUT_MS = 3_000;

    private final KafkaAdmin kafkaAdmin;
    private volatile AdminClient admin;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public Health health() {
        try {
            int nodes = admin().describeCluster(new DescribeClusterOptions().timeoutMs(TIMEOUT_MS))
                    .nodes().get(TIMEOUT_MS + 500L, TimeUnit.MILLISECONDS).size();
            return nodes > 0 ? Health.up().withDetail("nodes", nodes).build()
                    : Health.down().withDetail("reason", "no broker nodes").build();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Health.down(e).build();
        }
    }

    /** One long-lived client (reconnects by itself), created lazily with short timeouts. */
    private AdminClient admin() {
        AdminClient client = admin;
        if (client == null) {
            synchronized (this) {
                if (admin == null) {
                    Map<String, Object> config = new HashMap<>(kafkaAdmin.getConfigurationProperties());
                    config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, TIMEOUT_MS);
                    config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, TIMEOUT_MS);
                    admin = AdminClient.create(config);
                }
                client = admin;
            }
        }
        return client;
    }

    @Override
    public void destroy() {
        if (admin != null) {
            admin.close();
        }
    }
}
