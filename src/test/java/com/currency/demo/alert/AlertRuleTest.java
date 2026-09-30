package com.currency.demo.alert;

import com.currency.demo.alert.AlertDtos.Alert;
import com.currency.demo.pricing.PriceTick;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRuleTest {

    private static final Instant T0 = Instant.parse("2026-09-30T08:00:00Z");
    private static final Duration COOLDOWN = Duration.ofMinutes(5);

    private static Alert alert(Direction d, String threshold, Instant lastTriggered) {
        return new Alert(1, PriceTick.BTC_USD, d, new BigDecimal(threshold), T0, lastTriggered);
    }

    private static PriceTick tick(String price, Instant at) {
        return new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD, new BigDecimal(price), "coinbase", at, at);
    }

    @Test
    void aboveFiresOnlyStrictlyAboveTheThreshold() {
        Alert above = alert(Direction.ABOVE, "90000", null);
        assertThat(AlertRule.fires(above, tick("90000.00", T0), COOLDOWN)).as("equal").isFalse();
        assertThat(AlertRule.fires(above, tick("90000.01", T0), COOLDOWN)).isTrue();
        assertThat(AlertRule.fires(above, tick("89999.99", T0), COOLDOWN)).isFalse();
    }

    @Test
    void belowFiresOnlyStrictlyBelowTheThreshold() {
        Alert below = alert(Direction.BELOW, "80000", null);
        assertThat(AlertRule.fires(below, tick("80000", T0), COOLDOWN)).as("equal").isFalse();
        assertThat(AlertRule.fires(below, tick("79999.99", T0), COOLDOWN)).isTrue();
        assertThat(AlertRule.fires(below, tick("80000.01", T0), COOLDOWN)).isFalse();
    }

    @Test
    void cooldownBoundaryIsExactlyFiveMinutesOfEventTime() {
        Alert fired = alert(Direction.ABOVE, "90000", T0);
        assertThat(AlertRule.fires(fired, tick("95000", T0.plus(COOLDOWN).minusMillis(1)), COOLDOWN))
                .as("4:59.999").isFalse();
        assertThat(AlertRule.fires(fired, tick("95000", T0.plus(COOLDOWN)), COOLDOWN))
                .as("exactly 5:00 and still above").isTrue();
    }

    @Test
    void afterCooldownItFiresAgainOnlyIfTheConditionStillHolds() {
        Alert fired = alert(Direction.ABOVE, "90000", T0);
        assertThat(AlertRule.fires(fired, tick("89000", T0.plus(Duration.ofMinutes(10))), COOLDOWN)).isFalse();
    }

    @Test
    void aTickOlderThanTheLastTriggerNeverFires() {
        Alert fired = alert(Direction.ABOVE, "90000", T0);
        assertThat(AlertRule.fires(fired, tick("95000", T0.minusSeconds(1)), COOLDOWN)).isFalse();
    }
}
