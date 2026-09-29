package com.currency.demo.config;

import com.currency.demo.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard for requirement 18: automated tests must never reach real price or FX sources.
 *
 * <p>Deliberately has NO {@code @ActiveProfiles}: it proves the "test" profile is
 * switched on globally by surefire, so any test class gets the safe settings from
 * {@code application-test.yml} even if its author forgot. Later tasks extend this
 * guard (no real WebSocket client started, FX schedule not running).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NoExternalCallsGuardTest extends IntegrationTest {

    @Autowired
    Environment environment;

    @Autowired
    AppProperties props;

    @Test
    void testProfileIsActiveWithoutAnnotation() {
        assertThat(environment.getActiveProfiles()).contains("test");
    }

    @Test
    void everyExternalUrlPointsAtLoopback() {
        List<URI> external = List.of(
                props.feed().primary().url(),
                props.feed().backup().url(),
                props.fx().url());

        assertThat(external).allSatisfy(uri -> assertThat(uri.getHost()).isEqualTo("127.0.0.1"));
    }

    @Test
    void configuredKafkaAndDatabaseFallBackToLoopbackPortOne() {
        // The effective connections come from Testcontainers via @ServiceConnection; these are
        // the fall-back values a test would use if it forgot to extend IntegrationTest.
        assertThat(environment.getProperty("spring.kafka.bootstrap-servers")).isEqualTo("127.0.0.1:1");
        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://127.0.0.1:1/none");
    }

    @Test
    void backgroundJobsThatCallExternalSourcesAreDisabled() {
        assertThat(props.ingest().enabled()).isFalse();
        assertThat(props.fx().refreshEnabled()).isFalse();
    }
}
