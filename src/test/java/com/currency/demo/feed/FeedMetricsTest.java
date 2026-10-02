package com.currency.demo.feed;

import com.currency.demo.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** The ingest metrics that the Kubernetes alerts rely on (spec k8s-gitops-cicd, task 2). */
class FeedMetricsTest {

    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");

    private final MutableClock clock = new MutableClock(T0);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private FakeFeedClient coinbase;
    private FakeFeedClient kraken;
    private FeedManager manager;

    private FeedManager newManager() {
        manager = new FeedManager((parser, listener) -> {
            FakeFeedClient fake = new FakeFeedClient(parser.sourceName(), listener, clock);
            if (parser.sourceName().equals("coinbase")) {
                coinbase = fake;
            } else {
                kraken = fake;
            }
            return fake;
        }, new CoinbaseMessageParser(), new KrakenMessageParser(), mock(TickPublisher.class),
                mock(FeedStatusPublisher.class), clock,
                Duration.ofSeconds(10), Duration.ofSeconds(60), Duration.ofSeconds(15));
        return manager;
    }

    @AfterEach
    void stop() {
        if (manager != null) {
            manager.stop();
        }
    }

    private double gauge(String name, String... tags) {
        return registry.get(name).tags(tags).gauge().value();
    }

    @Test
    void whereIngestIsDisabledOnlyIngestActiveExistsAndIsZero() {
        FeedMetrics.register(null, registry);

        assertThat(gauge("cube.feed.ingest.active")).isEqualTo(1); // DELIBERATELY WRONG (task 13 test PR)
        assertThat(registry.find("cube.feed.last.tick.seconds").gauge()).isNull();
        assertThat(registry.find("cube.feed.active.source").gauge()).isNull();
    }

    @Test
    void ingestActiveIsOneOnlyWhileTheFeedManagerRuns() {
        FeedMetrics.register(newManager(), registry);
        assertThat(gauge("cube.feed.ingest.active")).isZero();

        manager.start();
        assertThat(gauge("cube.feed.ingest.active")).isEqualTo(1);

        manager.stop();
        assertThat(gauge("cube.feed.ingest.active")).isZero();
    }

    @Test
    void lastTickIsNaNUntilTheActiveSourceTicksThenTheLocalReceiveTime() {
        FeedMetrics.register(newManager(), registry);
        assertThat(gauge("cube.feed.last.tick.seconds")).isNaN();

        clock.advance(Duration.ofSeconds(3));
        kraken.tick();                                            // backup is not the active source
        assertThat(gauge("cube.feed.last.tick.seconds")).isNaN();

        coinbase.tick();
        assertThat(gauge("cube.feed.last.tick.seconds")).isEqualTo(T0.plusSeconds(3).getEpochSecond());

        clock.advance(Duration.ofMillis(1_500));
        coinbase.tick();
        assertThat(gauge("cube.feed.last.tick.seconds")).isEqualTo(T0.plusMillis(4_500).toEpochMilli() / 1000.0);
    }

    @Test
    void activeSourceFollowsAFailover() {
        FeedMetrics.register(newManager(), registry);
        coinbase.tick();
        kraken.tick();
        manager.check();
        assertThat(gauge("cube.feed.active.source", "source", "coinbase")).isEqualTo(1);
        assertThat(gauge("cube.feed.active.source", "source", "kraken")).isZero();

        // coinbase goes quiet for the stale threshold while kraken keeps ticking -> failover
        for (int i = 0; i < 10; i++) {
            clock.advance(Duration.ofSeconds(1));
            kraken.tick();
            manager.check();
        }
        assertThat(manager.activeSource()).isEqualTo("kraken");
        assertThat(gauge("cube.feed.active.source", "source", "coinbase")).isZero();
        assertThat(gauge("cube.feed.active.source", "source", "kraken")).isEqualTo(1);
    }
}
