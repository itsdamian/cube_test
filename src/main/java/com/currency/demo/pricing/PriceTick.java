package com.currency.demo.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * One trade price received from an exchange - the event written to {@code btc.price.ticks}.
 *
 * @param eventId    unique id assigned on receipt; makes DB writes idempotent (at-least-once delivery)
 * @param pair       currency pair, always {@code BTC-USD} in this project
 * @param price      trade price in USD; {@link BigDecimal} because money must not use binary floating point
 * @param source     exchange that produced it, e.g. {@code coinbase} or {@code kraken}
 * @param eventTime  when the trade happened, according to the exchange (used for candles)
 * @param receivedAt when our app received it (used for staleness detection and tie-breaking)
 */
public record PriceTick(UUID eventId, String pair, BigDecimal price, String source,
                        Instant eventTime, Instant receivedAt) {

    public static final String BTC_USD = "BTC-USD";

    public PriceTick {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(pair, "pair");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(source, "source");
        // PostgreSQL stores microseconds and the JDBC driver ROUNDS extra nanoseconds, which could
        // move a tick at 12:04:59.9999996 into the 12:05 candle in the database while Kafka Streams
        // keeps it in 12:04. Truncating here once makes every consumer see the same instant.
        eventTime = Objects.requireNonNull(eventTime, "eventTime").truncatedTo(ChronoUnit.MICROS);
        receivedAt = Objects.requireNonNull(receivedAt, "receivedAt").truncatedTo(ChronoUnit.MICROS);
    }
}
