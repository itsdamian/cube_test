package com.currency.demo.feed;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Ingest metrics for Prometheus (spec k8s-gitops-cicd, requirement 20 / 22).
 *
 * <ul>
 *   <li>{@code cube_feed_ingest_active} - 1 in the process that runs the exchange connections,
 *       0 everywhere else (the API replicas). Registered in every process, so "two processes
 *       ingesting" is a simple {@code count(... == 1) > 1} alert.</li>
 *   <li>{@code cube_feed_last_tick_seconds} - epoch seconds when this process last received a
 *       trade from the active source and handed it to Kafka; NaN before the first one.
 *       Alerting on {@code time() - this > 60} catches a stalled ingest.</li>
 *   <li>{@code cube_feed_active_source{source}} - 1 for the source in use, 0 for the other.</li>
 * </ul>
 * The last two only exist where ingest runs ({@code app.ingest.enabled=true}).
 */
@Component
public class FeedMetrics {

    public FeedMetrics(ObjectProvider<FeedManager> feedManager, MeterRegistry registry) {
        register(feedManager.getIfAvailable(), registry);
    }

    /** Registers the gauges; {@code manager} is null where ingest is disabled. */
    static void register(FeedManager manager, MeterRegistry registry) {
        Gauge.builder("cube.feed.ingest.active", () -> manager != null && manager.isRunning() ? 1 : 0)
                .description("1 if this process runs the exchange connections (must be exactly one per environment)")
                .register(registry);
        if (manager == null) {
            return;
        }
        Gauge.builder("cube.feed.last.tick.seconds", manager,
                        m -> m.lastTickPublishedAt().map(t -> t.toEpochMilli() / 1000.0).orElse(Double.NaN))
                .description("Epoch seconds of the last trade received from the active source and published")
                .register(registry);
        for (PriceFeedClient client : manager.clients()) {
            String source = client.sourceName();
            Gauge.builder("cube.feed.active.source", manager, m -> source.equals(m.activeSource()) ? 1 : 0)
                    .tag("source", source)
                    .description("1 for the exchange currently used as the price source")
                    .register(registry);
        }
    }
}
