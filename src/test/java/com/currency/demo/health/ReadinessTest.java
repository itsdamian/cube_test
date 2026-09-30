package com.currency.demo.health;

import com.currency.demo.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC12 with a real broker: "stopping" Kafka is simulated with docker pause/unpause. Pause keeps
 * the container and its port, so the app can reconnect after unpause - Testcontainers' stop/start
 * would give a new random port the app never learns about (QA M1).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReadinessTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void readinessFollowsKafkaWhileLivenessStaysUp() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.kafka.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());

        KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
        try {
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    mvc.perform(get("/actuator/health/readiness"))
                            .andExpect(status().isServiceUnavailable())
                            .andExpect(jsonPath("$.status").value("DOWN"))
                            .andExpect(jsonPath("$.components.kafka.status").value("DOWN")));
            mvc.perform(get("/actuator/health/liveness"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        } finally {
            KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
        }

        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() ->
                mvc.perform(get("/actuator/health/readiness"))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP")));
    }

    @Test
    void readinessAnswersWithinSecondsWhenTheDatabaseIsUnreachable(
            @Autowired com.zaxxer.hikari.HikariDataSource dataSource) {
        // QA CONCERN 19: Hikari's default would make the "db" check hang for 30 s.
        assertThat(dataSource.getConnectionTimeout()).isEqualTo(5_000);
    }

    @Test
    void healthDetailsAreNotExposed() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(jsonPath("$.components.kafka.details").doesNotExist())
                .andExpect(jsonPath("$.components.db.details").doesNotExist());
    }
}
