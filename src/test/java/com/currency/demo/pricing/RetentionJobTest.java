package com.currency.demo.pricing;

import com.currency.demo.candle.Candle;
import com.currency.demo.candle.CandleRepository;
import com.currency.demo.support.IntegrationTest;
import com.currency.demo.support.MutableClock;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Purge logic against its OWN database (created in the shared PostgreSQL container and migrated
 * with Flyway): "delete everything older than X" would otherwise wipe other test classes' ticks.
 * Retention is 30 days; the clock is set so the cutoff is exactly 2040-01-31T00:00Z.
 */
class RetentionJobTest extends IntegrationTest {

    static final Instant NOW = Instant.parse("2040-03-01T00:00:00Z");
    static final Instant CUTOFF = NOW.minus(Duration.ofDays(30));   // 2040-01-31T00:00:00Z

    static JdbcTemplate jdbc;

    @BeforeAll
    static void ownDatabase() {
        String db = "retention_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()))
                .execute("CREATE DATABASE " + db);
        String url = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + db + "$1");
        DriverManagerDataSource ds = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
    }

    @Test
    void deletesAllExpiredTicksInBatchesAndKeepsBoundaryFreshTicksAndCandles() {
        PriceTickRepository ticks = new PriceTickRepository(jdbc);
        CandleRepository candles = new CandleRepository(jdbc);

        List<PriceTick> expired = new ArrayList<>();
        for (int i = 0; i < 25_000; i++) {                       // more than two batches of 10,000
            expired.add(tick(CUTOFF.minus(Duration.ofDays(5)).plusMillis(100L * i)));
        }
        expired.add(tick(CUTOFF.minusNanos(1_000)));             // 1 µs before the cutoff -> expired
        ticks.insertAll(expired);
        ticks.insertAll(List.of(tick(CUTOFF)));                  // exactly at the cutoff -> kept
        List<PriceTick> fresh = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            fresh.add(tick(CUTOFF.plusSeconds(60L * (i + 1))));
        }
        ticks.insertAll(fresh);
        candles.upsertAll(List.of(
                candle(CUTOFF.minus(Duration.ofDays(20))),          // old candle: must survive
                candle(CUTOFF.plus(Duration.ofDays(1)))));

        RetentionJob.Result result = new RetentionJob(jdbc, new MutableClock(NOW), Duration.ofDays(30)).purge();

        assertThat(result.cutoff()).isEqualTo(CUTOFF);
        assertThat(result.deleted()).isEqualTo(25_001);
        assertThat(result.batches()).isEqualTo(4);               // 10k + 10k + 5,001 + one empty check
        assertThat(count("SELECT count(*) FROM price_tick WHERE event_time < ?", CUTOFF)).isZero();
        assertThat(count("SELECT count(*) FROM price_tick WHERE event_time = ?", CUTOFF)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM price_tick WHERE event_time > ?", CUTOFF)).isEqualTo(100);
        assertThat(count("SELECT count(*) FROM candle", null)).isEqualTo(2);

        RetentionJob.Result again = new RetentionJob(jdbc, new MutableClock(NOW), Duration.ofDays(30)).purge();
        assertThat(again.deleted()).as("idempotent").isZero();
    }

    private static int count(String sql, Instant at) {
        return at == null ? jdbc.queryForObject(sql, Integer.class)
                : jdbc.queryForObject(sql, Integer.class, PriceTickRepository.utc(at));
    }

    private static PriceTick tick(Instant t) {
        return new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD, new BigDecimal("84000"), "coinbase", t, t);
    }

    private static Candle candle(Instant open) {
        return new Candle(PriceTick.BTC_USD, "1m", open, open.plusSeconds(60), BigDecimal.ONE, BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.TEN, 3);
    }
}
