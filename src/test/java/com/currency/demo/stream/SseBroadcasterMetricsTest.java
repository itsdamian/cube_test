package com.currency.demo.stream;

import com.currency.demo.pricing.FeedStatusTracker;
import com.currency.demo.pricing.PriceTick;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The SSE metrics behind the {@code PricePushStalled} alert (spec k8s-gitops-cicd, task 2).
 * The key property: the "last push" time moves with every new price even when no browser is
 * connected, so a quiet night with nobody on the page never looks like a frozen push loop.
 */
class SseBroadcasterMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final SseBroadcaster broadcaster =
            new SseBroadcaster(new FeedStatusTracker(Clock.systemUTC()), registry, Clock.systemUTC());

    @AfterEach
    void stop() {
        broadcaster.destroy();
    }

    private static PriceTick tick(String price) {
        Instant now = Instant.now();
        return new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD, new BigDecimal(price), "coinbase", now, now);
    }

    private double lastPush() {
        return registry.get("cube.sse.last.push.seconds").gauge().value();
    }

    @Test
    void lastPushMovesWithNewPricesEvenWithNoClientConnected() {
        assertThat(broadcaster.connectionCount()).isZero();
        assertThat(lastPush()).isNaN();

        double before = Instant.now().toEpochMilli() / 1000.0;
        broadcaster.onTicks(List.of(tick("84000.01")));
        await().atMost(Duration.ofSeconds(3)).until(() -> !Double.isNaN(lastPush()));
        double first = lastPush();
        assertThat(first).isGreaterThanOrEqualTo(before);

        broadcaster.onTicks(List.of(tick("84000.02")));
        await().atMost(Duration.ofSeconds(3)).until(() -> lastPush() > first);
        // No browser was connected, so nothing was actually written.
        assertThat(registry.get("cube.sse.prices.pushed").counter().count()).isZero();
    }

    @Test
    void lastPushDoesNotMoveWithoutANewPrice() throws InterruptedException {
        broadcaster.onTicks(List.of(tick("84000.01")));
        await().atMost(Duration.ofSeconds(3)).until(() -> !Double.isNaN(lastPush()));
        double first = lastPush();

        Thread.sleep(3 * SseBroadcaster.PRICE_INTERVAL_MS);   // several push periods, same price
        assertThat(lastPush()).isEqualTo(first);
    }

    @Test
    void countsPricesWrittenToBrowsersAndOpenConnections() {
        SseBroadcasterResilienceTest.RecordingEmitter a = new SseBroadcasterResilienceTest.RecordingEmitter();
        SseBroadcasterResilienceTest.RecordingEmitter b = new SseBroadcasterResilienceTest.RecordingEmitter();
        broadcaster.register(a);
        broadcaster.register(b);
        assertThat(registry.get("cube.sse.connections").gauge().value()).isEqualTo(2);

        broadcaster.onTicks(List.of(tick("84000.01")));
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(a.prices).contains(new BigDecimal("84000.01"));
            assertThat(b.prices).contains(new BigDecimal("84000.01"));
        });
        // Exactly one count per price event written to a browser. Not simply "2": register()
        // sends the latest known price to a new connection on the sender thread, and if that task
        // runs after onTicks() stored the tick, a browser legitimately gets it twice (initial +
        // broadcast) - CI hit this race on main (run 37185194261).
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(registry.get("cube.sse.prices.pushed").counter().count())
                        .isEqualTo(a.prices.size() + b.prices.size()));
        assertThat(a.prices.size() + b.prices.size()).isBetween(2, 4);
    }
}
