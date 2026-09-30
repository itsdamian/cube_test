package com.currency.demo.pricing;

import com.currency.demo.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * QA M6: the job really runs on its schedule, with an interval taken from APP_RETENTION_INTERVAL.
 * Nothing calls purge() here. Only a tick from 2020 is old enough to be removed; every other
 * test's data is within the last 30 days or in the future, so the shared database is safe.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "APP_RETENTION_INTERVAL=PT1S")
class RetentionScheduleTest extends IntegrationTest {

    @Autowired
    PriceTickRepository ticks;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void scheduledRunDeletesExpiredTicksWithoutBeingCalled() {
        String source = "ret-" + UUID.randomUUID().toString().substring(0, 8);
        Instant old = Instant.parse("2020-01-01T00:00:00Z");
        Instant recent = Instant.now().minus(Duration.ofDays(1));
        ticks.insertAll(List.of(
                new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD, BigDecimal.ONE, source, old, old),
                new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD, BigDecimal.ONE, source, recent, recent)));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT count(*) FROM price_tick WHERE source = ? AND event_time < '2021-01-01'",
                        Integer.class, source)).isZero());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM price_tick WHERE source = ?", Integer.class, source))
                .as("the recent tick stays").isEqualTo(1);
    }
}
