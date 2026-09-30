package com.currency.demo.stream;

import com.currency.demo.pricing.PriceTick;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payloads of the SSE stream ({@code GET /api/stream}). Event names: {@code price}, {@code status}
 * ({@link com.currency.demo.feed.FeedStatus}) and {@code alert}
 * ({@link com.currency.demo.alert.AlertDtos.AlertTriggered}).
 */
public final class StreamEvents {

    public static final String PRICE = "price";
    public static final String STATUS = "status";
    public static final String ALERT = "alert";

    private StreamEvents() {
    }

    /** Event {@code price}: the newest BTC-USD trade. */
    public record Price(String pair, BigDecimal price, String source, Instant eventTime) {

        public static Price of(PriceTick tick) {
            return new Price(tick.pair(), tick.price(), tick.source(), tick.eventTime());
        }
    }
}
