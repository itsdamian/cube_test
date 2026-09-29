package com.currency.demo.pricing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.currency.demo.pricing.PriceTickRepository.utc;

/**
 * Read queries on {@code price_tick}, all served by the (pair, event_time) index.
 *
 * <p>"Last" always means the maximum of (event_time, received_at, event_id) - the same
 * tie-break Kafka Streams uses for candle open/close, so the two always agree.
 */
@Repository
public class PriceQueryRepository {

    /** One stored tick plus its row id (the id makes the keyset cursor unique). */
    public record Row(long id, Instant eventTime, BigDecimal price, String source) {
    }

    public record TrendRow(Instant bucketStart, Instant eventTime, BigDecimal price) {
    }

    private final JdbcTemplate jdbc;

    public PriceQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Row> latest(String pair) {
        return jdbc.query("""
                SELECT id, event_time, price, source FROM price_tick
                WHERE pair = ?
                ORDER BY event_time DESC, received_at DESC, event_id DESC
                LIMIT 1
                """, PriceQueryRepository::row, pair).stream().findFirst();
    }

    /**
     * Keyset ("seek") pagination: rows strictly after (afterTime, afterId) in (event_time, id)
     * order. Unlike OFFSET, each page costs the same however deep you go, and rows inserted
     * meanwhile cannot shift items between pages.
     */
    public List<Row> page(String pair, Instant from, Instant to, Instant afterTime, long afterId, int limit) {
        return jdbc.query("""
                SELECT id, event_time, price, source FROM price_tick
                WHERE pair = ? AND event_time >= ? AND event_time < ?
                  AND (event_time, id) > (?, ?)
                ORDER BY event_time, id
                LIMIT ?
                """, PriceQueryRepository::row, pair, utc(from), utc(to), utc(afterTime), afterId, limit);
    }

    /**
     * Server-side downsampling: {@code date_bin} cuts [from, to) into buckets of {@code bucket}
     * width aligned to {@code from}; {@code DISTINCT ON} keeps the last tick of each bucket.
     */
    public List<TrendRow> trend(String pair, Instant from, Instant to, Duration bucket) {
        long micros = bucket.toNanos() / 1_000;
        return jdbc.query("""
                SELECT DISTINCT ON (bucket) date_bin(? * interval '1 microsecond', event_time, ?) AS bucket,
                       event_time, price
                FROM price_tick
                WHERE pair = ? AND event_time >= ? AND event_time < ?
                ORDER BY bucket, event_time DESC, received_at DESC, event_id DESC
                """, (rs, n) -> new TrendRow(instant(rs, "bucket"), instant(rs, "event_time"), rs.getBigDecimal("price")),
                micros, utc(from), pair, utc(from), utc(to));
    }

    private static Row row(ResultSet rs, int n) throws SQLException {
        return new Row(rs.getLong("id"), instant(rs, "event_time"), rs.getBigDecimal("price"), rs.getString("source"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }
}
