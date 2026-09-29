package com.currency.demo.config;

import org.junit.jupiter.api.Nested;
import com.currency.demo.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@code application.yml} binds into {@link AppProperties} with the
 * documented defaults, and that the plan's environment-variable names override them.
 *
 * <p>Environment variables cannot be set from inside a running JVM, so the override
 * test supplies the same names (e.g. {@code APP_RETENTION_TICKS}) as test properties:
 * the {@code ${APP_RETENTION_TICKS:P30D}} placeholders resolve against every property
 * source, and OS environment variables are just one of those sources.
 */
class AppPropertiesTest {

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
    class Defaults extends IntegrationTest {

        @Autowired
        AppProperties props;

        @Test
        void bindsDocumentedDefaults() {
            assertThat(props.feed().primary().name()).isEqualTo("coinbase");
            assertThat(props.feed().backup().name()).isEqualTo("kraken");
            assertThat(props.feed().staleThreshold()).isEqualTo(Duration.ofSeconds(10));
            assertThat(props.feed().priceStaleThreshold()).isEqualTo(Duration.ofSeconds(60));
            assertThat(props.feed().recoveryPeriod()).isEqualTo(Duration.ofSeconds(15));
            assertThat(props.feed().idleTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(props.feed().reconnectInitialBackoff()).isEqualTo(Duration.ofSeconds(1));
            assertThat(props.feed().reconnectMaxBackoff()).isEqualTo(Duration.ofSeconds(30));
            assertThat(props.fx().refreshInterval()).isEqualTo(Duration.ofMinutes(30));
            assertThat(props.retention().ticks()).isEqualTo(Duration.ofDays(30));
            assertThat(props.retention().interval()).isEqualTo(Duration.ofHours(1));
            assertThat(props.alert().cooldown()).isEqualTo(Duration.ofMinutes(5));
            assertThat(props.streams().enabled()).isTrue();
            assertThat(props.persist().enabled()).isTrue();
            assertThat(props.alerts().enabled()).isTrue();
            assertThat(props.feed().publishQueueCapacity()).isEqualTo(10_000);
            assertThat(props.kafka().replicationFactor()).isEqualTo(1);
            assertThat(props.kafka().tickPartitions()).isEqualTo(3);
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
            "APP_RETENTION_TICKS=PT5M",
            "APP_RETENTION_INTERVAL=PT1S",
            "APP_FEED_STALE_THRESHOLD=20s",
            "APP_ALERT_COOLDOWN=30s",
            "APP_STREAMS_ENABLED=false"
    })
    class EnvironmentOverrides extends IntegrationTest {

        @Autowired
        AppProperties props;

        @Test
        void environmentVariableNamesOverrideDefaults() {
            assertThat(props.retention().ticks()).isEqualTo(Duration.ofMinutes(5));
            assertThat(props.retention().interval()).isEqualTo(Duration.ofSeconds(1));
            assertThat(props.feed().staleThreshold()).isEqualTo(Duration.ofSeconds(20));
            assertThat(props.alert().cooldown()).isEqualTo(Duration.ofSeconds(30));
            assertThat(props.streams().enabled()).isFalse();
        }
    }
}
