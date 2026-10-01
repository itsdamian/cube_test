package com.currency.demo.health;

import com.currency.demo.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /actuator/prometheus} exposes the metrics the Kubernetes alerts and dashboard use
 * (spec k8s-gitops-cicd, task 2). The test profile has ingest disabled - like the API replicas -
 * so {@code cube_feed_ingest_active} is 0 and the ingest-only gauges are absent.
 * (Spring Boot switches metric exporters off in tests unless asked: {@link AutoConfigureObservability}.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
class PrometheusEndpointTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void exposesTheCubeMetricsInPrometheusFormat() throws Exception {
        String body = mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .containsPattern("(?m)^cube_feed_ingest_active(\\{[^}]*\\})? 0\\.0$")
                .contains("cube_sse_last_push_seconds")
                .contains("cube_sse_prices_pushed_total")
                .contains("cube_sse_connections")
                .contains("jvm_memory_used_bytes")
                .doesNotContain("cube_feed_last_tick_seconds")
                .doesNotContain("cube_feed_active_source");
    }
}
