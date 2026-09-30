package com.currency.demo.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Plain JDBC access to {@code price_tick}. Ticks arrive in bursts of hundreds, so they are
 * written with one batched statement instead of JPA's one-INSERT-per-entity.
 */
@Repository
public class PriceTickRepository {

    private static final String INSERT = """
            INSERT INTO price_tick (event_id, pair, price, source, event_time, received_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
            """;

    private static final Logger log = LoggerFactory.getLogger(PriceTickRepository.class);

    private final JdbcTemplate jdbc;

    public PriceTickRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts all ticks in one batch; ticks whose event id already exists are skipped.
     * If the database rejects a row for good (e.g. a price too large for numeric(20,8)), the batch
     * is retried row by row so only that row is dropped (logged at ERROR) - one bad tick must
     * never block or lose the good ones.
     */
    public void insertAll(List<PriceTick> ticks) {
        if (ticks.isEmpty()) {
            return;
        }
        try {
            jdbc.batchUpdate(INSERT, ticks, ticks.size(), PriceTickRepository::bind);
        } catch (DataIntegrityViolationException batchFailed) {
            for (PriceTick tick : ticks) {
                try {
                    jdbc.update(INSERT, ps -> bind(ps, tick));
                } catch (DataIntegrityViolationException rowFailed) {
                    log.error("Dropping tick the database rejects: {} ({})", tick, rowFailed.getMostSpecificCause().getMessage());
                }
            }
        }
    }

    private static void bind(PreparedStatement ps, PriceTick t) throws SQLException {
        ps.setObject(1, t.eventId());
        ps.setString(2, t.pair());
        ps.setBigDecimal(3, t.price());
        ps.setString(4, t.source());
        ps.setObject(5, utc(t.eventTime()));
        ps.setObject(6, utc(t.receivedAt()));
    }

    /** The PostgreSQL driver maps OffsetDateTime to timestamptz unambiguously. */
    static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
