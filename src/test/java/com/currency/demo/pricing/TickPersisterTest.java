package com.currency.demo.pricing;

import com.currency.demo.config.Topics;
import com.currency.demo.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Kafka -> TickPersister -> PostgreSQL with real containers: duplicates (same event id,
 * as after a redelivery) are stored once and every column round-trips exactly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TickPersisterTest extends IntegrationTest {

    @Autowired
    KafkaTemplate<String, Object> kafka;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void storesEachEventOnceWithExactValues() {
        // A source name unique to this run isolates our rows in the shared test database.
        String source = "t9-" + UUID.randomUUID().toString().substring(0, 8);
        Instant start = Instant.parse("2026-09-29T10:00:00.123456Z");
        List<PriceTick> distinct = new ArrayList<>();
        for (int i = 0; i < 480; i++) {
            Instant t = start.plusMillis(250L * i);
            distinct.add(new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD,
                    new BigDecimal("84000.12345678").add(BigDecimal.valueOf(i)), source, t, t.plusMillis(40)));
        }
        List<PriceTick> sent = new ArrayList<>(distinct);
        sent.addAll(distinct.subList(0, 20));              // 20 duplicates -> 500 records in total

        sent.forEach(t -> kafka.send(Topics.PRICE_TICKS, t.pair(), t));
        kafka.flush();

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(count(source)).isEqualTo(480));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(count(source)).as("no late duplicates").isEqualTo(480));

        List<PriceTick> stored = jdbc.query(
                "SELECT event_id, pair, price, source, event_time, received_at FROM price_tick WHERE source = ?",
                TickPersisterTest::map, source);
        assertThat(stored)
                .usingElementComparator(Comparator.comparing(PriceTick::eventId)
                        .thenComparing(PriceTick::pair)
                        .thenComparing(PriceTick::price, BigDecimal::compareTo)
                        .thenComparing(PriceTick::source)
                        .thenComparing(PriceTick::eventTime)
                        .thenComparing(PriceTick::receivedAt))
                .containsExactlyInAnyOrderElementsOf(distinct.stream().map(TickPersisterTest::asStoredByPostgres).toList());
    }

    private int count(String source) {
        return jdbc.queryForObject("SELECT count(*) FROM price_tick WHERE source = ?", Integer.class, source);
    }

    private static PriceTick map(ResultSet rs, int row) throws SQLException {
        return new PriceTick(rs.getObject("event_id", UUID.class), rs.getString("pair"), rs.getBigDecimal("price"),
                rs.getString("source"), rs.getObject("event_time", OffsetDateTime.class).toInstant(),
                rs.getObject("received_at", OffsetDateTime.class).toInstant());
    }

    /** PostgreSQL timestamps have microsecond precision. */
    private static PriceTick asStoredByPostgres(PriceTick t) {
        return new PriceTick(t.eventId(), t.pair(), t.price(), t.source(),
                t.eventTime().truncatedTo(ChronoUnit.MICROS), t.receivedAt().truncatedTo(ChronoUnit.MICROS));
    }
}
