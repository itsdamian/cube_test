package com.currency.demo.pricing;

import com.currency.demo.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static com.currency.demo.pricing.PriceTickRepository.utc;

/**
 * Deletes raw ticks older than {@code app.retention.ticks} (default 30 days, spec 8a).
 * Candles are never touched.
 *
 * <p>Runs {@code app.retention.initial-delay} after startup (default 1 min - so frequent
 * restarts cannot postpone it forever) and then every {@code app.retention.interval} (default 1 h).
 * The {@code pair = ?} condition lets PostgreSQL use the (pair, event_time) index. Each run deletes in batches of
 * {@link #BATCH_SIZE} and <b>loops until nothing old is left</b>: a single batch per run could
 * fall behind the insert rate (10 ticks/s = 36,000 per hour) and the table would grow forever.
 * Small batches keep each transaction and its locks short. Deleting is idempotent, so it is
 * harmless if several instances run it.
 */
@Component
@ConditionalOnProperty(name = "app.persist.enabled", havingValue = "true")
public class RetentionJob {

    static final int BATCH_SIZE = 10_000;
    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    /** Outcome of one run. */
    public record Result(Instant cutoff, long deleted, int batches) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Duration retention;

    @Autowired // two constructors: tell Spring which one to use (the other is for tests)
    public RetentionJob(JdbcTemplate jdbc, Clock clock, AppProperties props) {
        this(jdbc, clock, props.retention().ticks());
    }

    RetentionJob(JdbcTemplate jdbc, Clock clock, Duration retention) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.retention = retention;
    }

    @Scheduled(initialDelayString = "${app.retention.initial-delay}", fixedDelayString = "${app.retention.interval}")
    public void scheduled() {
        Result result = purge();
        if (result.deleted() > 0) {
            log.info("Retention: deleted {} ticks older than {} in {} batch(es)",
                    result.deleted(), result.cutoff(), result.batches());
        }
    }

    /** Delete every tick with event_time strictly before now - retention. */
    public Result purge() {
        Instant cutoff = clock.instant().minus(retention);
        long total = 0;
        int batches = 0;
        int deleted;
        do {
            deleted = jdbc.update("""
                    DELETE FROM price_tick
                    WHERE id IN (SELECT id FROM price_tick WHERE pair = ? AND event_time < ? LIMIT ?)
                    """, PriceTick.BTC_USD, utc(cutoff), BATCH_SIZE);
            total += deleted;
            batches++;
        } while (deleted > 0);
        return new Result(cutoff, total, batches);
    }
}
