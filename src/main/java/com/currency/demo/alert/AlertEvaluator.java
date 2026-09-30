package com.currency.demo.alert;

import com.currency.demo.alert.AlertDtos.Alert;
import com.currency.demo.alert.AlertDtos.AlertTriggered;
import com.currency.demo.config.AppProperties;
import com.currency.demo.config.Topics;
import com.currency.demo.pricing.PriceTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Checks every tick against the alerts, on the backend, so alerts fire even when no browser is
 * open (spec 14a). Shared consumer group {@code <prefix>alert-evaluator}: exactly one instance evaluates
 * each tick. A new group starts at the LATEST offset - deploying it must not replay old prices.
 *
 * <p>For each firing: claim the trigger (atomic conditional UPDATE), store an unread
 * {@code alert_event}, then publish {@link AlertTriggered} for the SSE push. The database is the
 * source of truth; the Kafka event is only the "tell open browsers now" signal.
 */
@Component
@ConditionalOnProperty(name = "app.alerts.enabled", havingValue = "true")
public class AlertEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluator.class);

    private final AlertRepository repository;
    private final TransactionTemplate tx;
    private final KafkaOperations<String, Object> kafka;
    private final Duration cooldown;

    public AlertEvaluator(AlertRepository repository, TransactionTemplate tx, KafkaOperations<String, Object> kafka,
                          AppProperties props) {
        this.repository = repository;
        this.tx = tx;
        this.kafka = kafka;
        this.cooldown = props.alert().cooldown();
    }

    @KafkaListener(id = "alert-evaluator", groupId = "${app.kafka.group-prefix}alert-evaluator", topics = Topics.PRICE_TICKS,
            containerFactory = "tickBatchListenerFactory", properties = "auto.offset.reset=latest")
    public void onTicks(List<PriceTick> batch) {
        List<PriceTick> ticks = batch.stream().filter(Objects::nonNull).toList();
        if (ticks.isEmpty()) {
            return;
        }
        List<Alert> alerts = new ArrayList<>(repository.findByPair(PriceTick.BTC_USD)); // one query per batch
        if (alerts.isEmpty()) {
            return;
        }
        for (PriceTick tick : ticks) {
            for (int i = 0; i < alerts.size(); i++) {
                Alert alert = alerts.get(i);
                if (AlertRule.fires(alert, tick, cooldown)) {
                    AlertTriggered fired = tx.execute(status -> trigger(alert, tick));
                    // Keep the local copy in sync so later ticks in this batch respect the cooldown.
                    alerts.set(i, new Alert(alert.id(), alert.pair(), alert.direction(), alert.threshold(),
                            alert.createdAt(), tick.eventTime()));
                    if (fired != null) {
                        log.info("Alert {} fired: {} {} at {}", alert.id(), alert.direction(), alert.threshold(),
                                tick.price());
                        kafka.send(Topics.ALERTS_TRIGGERED, PriceTick.BTC_USD, fired);
                    }
                }
            }
        }
    }

    /** @return the event, or null if another evaluation already fired it (or it was deleted) */
    private AlertTriggered trigger(Alert alert, PriceTick tick) {
        if (!repository.claimTrigger(alert.id(), tick.eventTime(), cooldown)) {
            return null;
        }
        long eventId = repository.insertEvent(alert, tick.price(), tick.eventTime());
        return new AlertTriggered(eventId, alert.id(), alert.pair(), alert.direction(), alert.threshold(),
                tick.price(), tick.eventTime());
    }
}
