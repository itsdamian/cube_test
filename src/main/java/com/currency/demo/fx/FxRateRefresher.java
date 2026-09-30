package com.currency.demo.fx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Downloads exchange rates on startup and then every {@code app.fx.refresh-interval}
 * (default 30 min, i.e. at least hourly as the spec requires).
 *
 * <p>If the source fails, the previously stored rates stay in use and a warning is logged.
 * Instead of waiting for the next regular run, one extra attempt is scheduled soon: after
 * {@link #RETRY_AFTER_FAILURE} normally, or after {@link #RETRY_AFTER_RATE_LIMIT} when the
 * provider answered 429 (its documented limit resets after 20 minutes). This matters most on a
 * brand-new installation, which has no stored rates to fall back on.
 */
public class FxRateRefresher {

    static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(1);
    static final Duration RETRY_AFTER_RATE_LIMIT = Duration.ofMinutes(20);
    private static final Logger log = LoggerFactory.getLogger(FxRateRefresher.class);

    private final ExchangeRateClient client;
    private final FxRateRepository repository;
    private final Clock clock;
    private final TaskScheduler scheduler;
    private final AtomicReference<ScheduledFuture<?>> pendingRetry = new AtomicReference<>();

    public FxRateRefresher(ExchangeRateClient client, FxRateRepository repository, Clock clock, TaskScheduler scheduler) {
        this.client = client;
        this.repository = repository;
        this.clock = clock;
        this.scheduler = scheduler;
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${app.fx.refresh-interval}")
    public void scheduled() {
        refresh();
    }

    /** @return true if new rates were stored */
    public boolean refresh() {
        try {
            FxSnapshot snapshot = client.fetch();
            repository.saveAll(snapshot, clock.instant());
            ScheduledFuture<?> retry = pendingRetry.getAndSet(null);
            if (retry != null) {
                retry.cancel(false);
            }
            log.info("Exchange rates refreshed: {} currencies, provider time {}",
                    snapshot.ratesPerUsd().size(), snapshot.providerUpdatedAt());
            return true;
        } catch (FxUnavailableException e) {
            Duration delay = e.isRateLimited() ? RETRY_AFTER_RATE_LIMIT : RETRY_AFTER_FAILURE;
            log.warn("Keeping previous exchange rates ({}); retrying in {}", e.getMessage(), delay);
            scheduleRetry(delay);
            return false;
        }
    }

    /** At most one extra attempt pending at a time. */
    private void scheduleRetry(Duration delay) {
        ScheduledFuture<?> current = pendingRetry.get();
        if (current != null && !current.isDone()) {
            return;
        }
        pendingRetry.set(scheduler.schedule(() -> {
            pendingRetry.set(null);
            refresh();
        }, clock.instant().plus(delay)));
    }
}
