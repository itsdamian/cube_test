package com.currency.demo.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PriceTickTest {

    @Test
    void timesAreTruncatedToMicrosecondsSoTheDatabaseCannotRoundIntoTheNextMinute() {
        Instant almostFive = Instant.parse("2026-09-29T12:04:59.999999600Z");

        PriceTick tick = new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD, BigDecimal.ONE, "coinbase",
                almostFive, almostFive);

        assertThat(tick.eventTime()).isEqualTo(Instant.parse("2026-09-29T12:04:59.999999Z"));
        assertThat(tick.receivedAt()).isEqualTo(Instant.parse("2026-09-29T12:04:59.999999Z"));
    }
}
