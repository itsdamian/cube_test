package com.currency.demo.fx;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** JDBC access to {@code fx_rate} (latest rate per currency). */
@Repository
public class FxRateRepository {

    /** A stored rate. */
    public record Rate(String code, BigDecimal ratePerUsd, Instant providerUpdatedAt, Instant fetchedAt) {
    }

    private final JdbcTemplate jdbc;

    public FxRateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveAll(FxSnapshot snapshot, Instant fetchedAt) {
        List<Map.Entry<String, BigDecimal>> entries = new ArrayList<>(snapshot.ratesPerUsd().entrySet());
        jdbc.batchUpdate("""
                INSERT INTO fx_rate (code, rate_per_usd, provider_updated_at, fetched_at) VALUES (?, ?, ?, ?)
                ON CONFLICT (code) DO UPDATE SET rate_per_usd = EXCLUDED.rate_per_usd,
                    provider_updated_at = EXCLUDED.provider_updated_at, fetched_at = EXCLUDED.fetched_at
                """, entries, entries.size(), (ps, e) -> {
            ps.setString(1, e.getKey());
            ps.setBigDecimal(2, e.getValue());
            ps.setObject(3, snapshot.providerUpdatedAt().atOffset(ZoneOffset.UTC));
            ps.setObject(4, fetchedAt.atOffset(ZoneOffset.UTC));
        });
    }

    public Map<String, Rate> findAll() {
        Map<String, Rate> rates = new HashMap<>();
        jdbc.query("SELECT code, rate_per_usd, provider_updated_at, fetched_at FROM fx_rate", rs -> {
            rates.put(rs.getString("code"), new Rate(rs.getString("code"), rs.getBigDecimal("rate_per_usd"),
                    rs.getObject("provider_updated_at", OffsetDateTime.class).toInstant(),
                    rs.getObject("fetched_at", OffsetDateTime.class).toInstant()));
        });
        return rates;
    }
}
