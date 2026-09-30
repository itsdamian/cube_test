package com.currency.demo.alert;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/** JSON shapes of /api/alerts and /api/alert-events. */
public final class AlertDtos {

    private AlertDtos() {
    }

    /** POST /api/alerts body, e.g. {"direction":"ABOVE","threshold":90000}. The pair is always BTC-USD. */
    public record CreateAlert(@NotNull Direction direction,
                              @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 8)
                              BigDecimal threshold) {
    }

    public record Alert(long id, String pair, Direction direction, BigDecimal threshold, Instant createdAt,
                        Instant lastTriggeredAt) {
    }

    /** One firing of an alert; {@code readAt == null} means unread. */
    public record AlertEvent(long id, long alertId, Direction direction, BigDecimal threshold, BigDecimal price,
                             Instant triggeredAt, Instant readAt) {
    }

    /** Kafka event on {@code btc.alerts.triggered}, pushed to open browsers via SSE. */
    public record AlertTriggered(long eventId, long alertId, String pair, Direction direction, BigDecimal threshold,
                                 BigDecimal price, Instant triggeredAt) {
    }
}
