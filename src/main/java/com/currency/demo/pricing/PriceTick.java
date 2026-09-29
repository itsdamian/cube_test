package com.currency.demo.pricing;

import java.math.BigDecimal;
import java.time.Instant;
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
        Objects.requireNonNull(eventTime, "eventTime");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }
}
