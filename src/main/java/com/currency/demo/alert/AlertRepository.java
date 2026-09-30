package com.currency.demo.alert;

import com.currency.demo.alert.AlertDtos.Alert;
import com.currency.demo.alert.AlertDtos.AlertEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** JDBC access to price_alert and alert_event. */
@Repository
public class AlertRepository {

    private final JdbcTemplate jdbc;

    public AlertRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Alert create(String pair, Direction direction, BigDecimal threshold, Instant now) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO price_alert (pair, direction, threshold, created_at) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, pair);
            ps.setString(2, direction.name());
            ps.setBigDecimal(3, threshold);
            ps.setObject(4, utc(now));
            return ps;
        }, key);
        long id = ((Number) Objects.requireNonNull(key.getKeys()).get("id")).longValue();
        return find(id).orElseThrow();
    }

    public Optional<Alert> find(long id) {
        return jdbc.query("SELECT * FROM price_alert WHERE id = ?", AlertRepository::alert, id).stream().findFirst();
    }

    public List<Alert> findAll() {
        return jdbc.query("SELECT * FROM price_alert ORDER BY id", AlertRepository::alert);
    }

    public List<Alert> findByPair(String pair) {
        return jdbc.query("SELECT * FROM price_alert WHERE pair = ? ORDER BY id", AlertRepository::alert, pair);
    }

    public boolean delete(long id) {
        return jdbc.update("DELETE FROM price_alert WHERE id = ?", id) == 1;
    }

    /**
     * Claims a trigger atomically: succeeds only if the alert still exists and is out of its
     * cooldown at {@code at}. Redelivered ticks or concurrent evaluators therefore cannot fire the
     * same alert twice.
     */
    public boolean claimTrigger(long alertId, Instant at, Duration cooldown) {
        return jdbc.update("""
                UPDATE price_alert SET last_triggered_at = ?
                WHERE id = ? AND (last_triggered_at IS NULL OR last_triggered_at <= ?)
                """, utc(at), alertId, utc(at.minus(cooldown))) == 1;
    }

    public long insertEvent(Alert alert, BigDecimal price, Instant at) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO alert_event (alert_id, direction, threshold, triggered_price, triggered_at)
                    VALUES (?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, alert.id());
            ps.setString(2, alert.direction().name());
            ps.setBigDecimal(3, alert.threshold());
            ps.setBigDecimal(4, price);
            ps.setObject(5, utc(at));
            return ps;
        }, key);
        return ((Number) Objects.requireNonNull(key.getKeys()).get("id")).longValue();
    }

    /** Most recent first, at most {@code limit}. */
    public List<AlertEvent> events(boolean unreadOnly, int limit) {
        return jdbc.query("SELECT * FROM alert_event " + (unreadOnly ? "WHERE read_at IS NULL " : "")
                + "ORDER BY triggered_at DESC, id DESC LIMIT ?", AlertRepository::event, limit);
    }

    public boolean markRead(long eventId, Instant now) {
        return jdbc.update("UPDATE alert_event SET read_at = COALESCE(read_at, ?) WHERE id = ?", utc(now), eventId) == 1;
    }

    public int markAllRead(Instant now) {
        return jdbc.update("UPDATE alert_event SET read_at = ? WHERE read_at IS NULL", utc(now));
    }

    private static Alert alert(ResultSet rs, int n) throws SQLException {
        return new Alert(rs.getLong("id"), rs.getString("pair"), Direction.valueOf(rs.getString("direction")),
                rs.getBigDecimal("threshold"), instant(rs, "created_at"), instant(rs, "last_triggered_at"));
    }

    private static AlertEvent event(ResultSet rs, int n) throws SQLException {
        return new AlertEvent(rs.getLong("id"), rs.getLong("alert_id"), Direction.valueOf(rs.getString("direction")),
                rs.getBigDecimal("threshold"), rs.getBigDecimal("triggered_price"), instant(rs, "triggered_at"),
                instant(rs, "read_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime t = rs.getObject(column, OffsetDateTime.class);
        return t == null ? null : t.toInstant();
    }

    private static OffsetDateTime utc(Instant i) {
        return i.atOffset(ZoneOffset.UTC);
    }
}
