package com.currency.demo.alert;

import com.currency.demo.alert.AlertDtos.Alert;
import com.currency.demo.pricing.PriceTick;

import java.time.Duration;

/**
 * When does a tick fire an alert? (spec 14)
 * <ul>
 *   <li>the condition holds: ABOVE = price &gt; threshold, BELOW = price &lt; threshold;</li>
 *   <li>and the alert is out of cooldown: never fired, or fired at least {@code cooldown} ago.
 *       Time is the tick's <em>event</em> time, so the rule does not depend on processing delays.</li>
 * </ul>
 * After the cooldown the alert fires again only if the condition still holds - it is level-based,
 * not edge-based.
 */
public final class AlertRule {

    private AlertRule() {
    }

    public static boolean fires(Alert alert, PriceTick tick, Duration cooldown) {
        if (!alert.pair().equals(tick.pair()) || !alert.direction().isMet(tick.price(), alert.threshold())) {
            return false;
        }
        return alert.lastTriggeredAt() == null
                || Duration.between(alert.lastTriggeredAt(), tick.eventTime()).compareTo(cooldown) >= 0;
    }
}
