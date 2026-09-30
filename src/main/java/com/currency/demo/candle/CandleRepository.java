package com.currency.demo.candle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** JDBC access to {@code candle}. */
@Repository
public class CandleRepository {

    private static final String UPSERT = """
            INSERT INTO candle (pair, interval_code, open_time, close_time, open, high, low, close, tick_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (pair, interval_code, open_time) DO UPDATE SET
                close_time = EXCLUDED.close_time, open = EXCLUDED.open, high = EXCLUDED.high,
                low = EXCLUDED.low, close = EXCLUDED.close, tick_count = EXCLUDED.tick_count
            """;

    private static final Logger log = LoggerFactory.getLogger(CandleRepository.class);

    private final JdbcTemplate jdbc;

    public CandleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Insert or replace (a re-delivered candle overwrites the same row - idempotent). */
    public void upsertAll(List<Candle> candles) {
        if (candles.isEmpty()) {
            return;
        }
        try {
            batch(candles);
        } catch (DataIntegrityViolationException batchFailed) {
            for (Candle candle : candles) {       // isolate the row the database rejects for good
                try {
                    batch(List.of(candle));
                } catch (DataIntegrityViolationException rowFailed) {
                    log.error("Dropping candle the database rejects: {} ({})", candle,
                            rowFailed.getMostSpecificCause().getMessage());
                }
            }
        }
    }

    private void batch(List<Candle> candles) {
        jdbc.batchUpdate(UPSERT, candles, candles.size(), (ps, c) -> {
            ps.setString(1, c.pair());
            ps.setString(2, c.interval());
            ps.setObject(3, utc(c.openTime()));
            ps.setObject(4, utc(c.closeTime()));
            ps.setBigDecimal(5, c.open());
            ps.setBigDecimal(6, c.high());
            ps.setBigDecimal(7, c.low());
            ps.setBigDecimal(8, c.close());
            ps.setInt(9, c.tickCount());
        });
    }

    /** Candles whose open_time is in [from, to), oldest first. */
    public List<Candle> find(String pair, String interval, Instant from, Instant to) {
        return jdbc.query("""
                SELECT pair, interval_code, open_time, close_time, open, high, low, close, tick_count
                FROM candle
                WHERE pair = ? AND interval_code = ? AND open_time >= ? AND open_time < ?
                ORDER BY open_time
                """, CandleRepository::map, pair, interval, utc(from), utc(to));
    }

    private static Candle map(ResultSet rs, int n) throws SQLException {
        return new Candle(rs.getString("pair"), rs.getString("interval_code"),
                rs.getObject("open_time", OffsetDateTime.class).toInstant(),
                rs.getObject("close_time", OffsetDateTime.class).toInstant(),
                rs.getBigDecimal("open"), rs.getBigDecimal("high"), rs.getBigDecimal("low"),
                rs.getBigDecimal("close"), rs.getInt("tick_count"));
    }

    private static OffsetDateTime utc(Instant i) {
        return i.atOffset(ZoneOffset.UTC);
    }
}
