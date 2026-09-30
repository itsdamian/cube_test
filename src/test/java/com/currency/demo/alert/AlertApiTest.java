package com.currency.demo.alert;

import com.currency.demo.alert.AlertDtos.Alert;
import com.currency.demo.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AlertApiTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AlertRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createListAndDelete() throws Exception {
        mvc.perform(post("/api/alerts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"ABOVE\",\"threshold\":90000}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.pair").value("BTC-USD"))
                .andExpect(jsonPath("$.direction").value("ABOVE"))
                .andExpect(jsonPath("$.threshold").value(90000))
                .andExpect(jsonPath("$.lastTriggeredAt").isEmpty());
        long id = repository.findAll().getLast().id();

        mvc.perform(get("/api/alerts")).andExpect(jsonPath("$[*].id", hasItem((int) id)));
        mvc.perform(delete("/api/alerts/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/alerts/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/alerts")).andExpect(jsonPath("$[*].id", not(hasItem((int) id))));
    }

    @Test
    void invalidAlertsAre400() throws Exception {
        for (String body : new String[]{
                "{\"threshold\":90000}",
                "{\"direction\":\"ABOVE\"}",
                "{\"direction\":\"ABOVE\",\"threshold\":0}",
                "{\"direction\":\"BELOW\",\"threshold\":-5}",
                "{\"direction\":\"SIDEWAYS\",\"threshold\":90000}"}) {
            mvc.perform(post("/api/alerts").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void unreadEventsCanBeMarkedReadOneByOneOrAll() throws Exception {
        Alert alert = repository.create("BTC-USD", Direction.BELOW, new BigDecimal("80000"), Instant.now());
        long e1 = repository.insertEvent(alert, new BigDecimal("79999"), Instant.parse("2026-09-30T08:00:00Z"));
        long e2 = repository.insertEvent(alert, new BigDecimal("79000"), Instant.parse("2026-09-30T08:06:00Z"));

        mvc.perform(get("/api/alert-events").param("unread", "true"))
                .andExpect(jsonPath("$[*].id", hasItem((int) e1)))
                .andExpect(jsonPath("$[*].id", hasItem((int) e2)))
                .andExpect(jsonPath("$[0].readAt").isEmpty());

        mvc.perform(post("/api/alert-events/{id}/read", e1)).andExpect(status().isNoContent());
        mvc.perform(get("/api/alert-events").param("unread", "true"))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) e1))))
                .andExpect(jsonPath("$[*].id", hasItem((int) e2)));
        mvc.perform(get("/api/alert-events"))              // all events include read ones
                .andExpect(jsonPath("$[*].id", hasItem((int) e1)));

        mvc.perform(post("/api/alert-events/read-all")).andExpect(status().isNoContent());
        mvc.perform(get("/api/alert-events").param("unread", "true"))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) e2))));
        mvc.perform(post("/api/alert-events/{id}/read", 99_999_999)).andExpect(status().isNotFound());
    }

    @Test
    void deletingAnAlertDeletesItsEvents() throws Exception {
        Alert alert = repository.create("BTC-USD", Direction.ABOVE, new BigDecimal("95000"), Instant.now());
        repository.insertEvent(alert, new BigDecimal("95001"), Instant.now());

        mvc.perform(delete("/api/alerts/{id}", alert.id())).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM alert_event WHERE alert_id = ?", Integer.class, alert.id()))
                .isZero();
    }
}
