package com.currency.demo.fx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;

/**
 * Downloads exchange rates on startup and then every {@code app.fx.refresh-interval}
 * (default 30 min, i.e. at least hourly as the spec requires). If the source fails (e.g. 429
 * rate limit, network down) the previously stored rates stay in use and a warning is logged;
 * the next run simply tries again.
 */
public class FxRateRefresher {

    private static final Logger log = LoggerFactory.getLogger(FxRateRefresher.class);

    private final ExchangeRateClient client;
    private final FxRateRepository repository;
    private final Clock clock;

    public FxRateRefresher(ExchangeRateClient client, FxRateRepository repository, Clock clock) {
        this.client = client;
        this.repository = repository;
        this.clock = clock;
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
            log.info("Exchange rates refreshed: {} currencies, provider time {}",
                    snapshot.ratesPerUsd().size(), snapshot.providerUpdatedAt());
            return true;
        } catch (FxUnavailableException e) {
            log.warn("Keeping previous exchange rates: {}", e.getMessage());
            return false;
        }
    }
}
