package com.currency.demo.stream;

import com.currency.demo.pricing.FeedStatusTracker;
import com.currency.demo.pricing.PriceTick;
import com.currency.demo.stream.StreamEvents.Price;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Task 32: one broken browser connection must never stop the price stream for everybody.
 *
 * <p>Found on the compose stack: status events kept arriving (so the page said LIVE) while the
 * price stood still. A {@link ScheduledExecutorService} cancels a periodic task for good as soon
 * as one run throws - silently. These tests use the real scheduler (250 ms price interval), no
 * Spring context and no Kafka: ticks are handed to {@code onTicks} directly.
 */
class SseBroadcasterResilienceTest {

    private final SseBroadcaster broadcaster = new SseBroadcaster(new FeedStatusTracker(Clock.systemUTC()));

    @AfterEach
    void stop() {
        broadcaster.destroy();
    }

    /** Records the prices it is sent. */
    static class RecordingEmitter extends SseEmitter {
        final List<BigDecimal> prices = new CopyOnWriteArrayList<>();

        @Override
        public void send(SseEventBuilder builder) {
            for (DataWithMediaType part : builder.build()) {
                if (part.getData() instanceof Price price) {
                    prices.add(price.price());
                }
            }
        }
    }

    /** A connection whose write fails with an unchecked exception (not IOException). */
    static class UncheckedFailingEmitter extends SseEmitter {
        @Override
        public void send(SseEventBuilder builder) {
            throw new IllegalArgumentException("simulated unchecked write failure");
        }
    }

    /** Write fails, and closing it fails too (e.g. the async request is already unusable). */
    static class FailingToCloseEmitter extends SseEmitter {
        @Override
        public void send(SseEventBuilder builder) throws IOException {
            throw new IOException("simulated broken pipe");
        }

        @Override
        public void completeWithError(Throwable ex) {
            throw new IllegalStateException("simulated: request already completed");
        }
    }

    private static PriceTick tick(String price) {
        Instant now = Instant.now();
        return new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD, new BigDecimal(price), "coinbase", now, now);
    }

    private void pushAndExpect(RecordingEmitter good, String price) {
        broadcaster.onTicks(List.of(tick(price)));
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(good.prices).contains(new BigDecimal(price)));
    }

    @Test
    void anUncheckedFailureOnOneConnectionDoesNotStopPricesForTheOthers() {
        RecordingEmitter good = new RecordingEmitter();
        broadcaster.register(new UncheckedFailingEmitter());
        broadcaster.register(good);

        pushAndExpect(good, "84000.01");
        // The broken connection is dropped, and the NEXT periods still push.
        assertThat(broadcaster.connectionCount()).isEqualTo(1);
        pushAndExpect(good, "84000.02");
        pushAndExpect(good, "84000.03");
    }

    @Test
    void aConnectionThatFailsEvenToCloseDoesNotStopPrices() {
        RecordingEmitter good = new RecordingEmitter();
        broadcaster.register(new FailingToCloseEmitter());
        broadcaster.register(good);

        pushAndExpect(good, "84000.11");
        assertThat(broadcaster.connectionCount()).isEqualTo(1);
        pushAndExpect(good, "84000.12");
    }

    @Test
    void aGuardedPeriodicTaskKeepsRunningAfterAFailedRun() throws InterruptedException {
        // The guard itself: without it the scheduler would run this task exactly once.
        AtomicInteger runs = new AtomicInteger();
        Runnable failsFirst = () -> {
            if (runs.incrementAndGet() == 1) {
                throw new IllegalStateException("simulated failure in the first run");
            }
        };
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            scheduler.scheduleAtFixedRate(SseBroadcaster.guarded("test", failsFirst), 0, 10, TimeUnit.MILLISECONDS);
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(runs.get()).isGreaterThanOrEqualTo(3));
        } finally {
            scheduler.shutdownNow();
        }
    }
}
