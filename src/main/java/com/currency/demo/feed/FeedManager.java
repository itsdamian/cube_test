package com.currency.demo.feed;

import com.currency.demo.feed.FeedStatus.State;
import com.currency.demo.pricing.PriceTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Chooses which exchange's prices are published (hot-standby failover).
 *
 * <p>Both clients stay connected; only ticks from the <em>active</em> source are handed to
 * {@link TickPublisher}. {@link #check()} runs once per second and applies these rules:
 * <ol>
 *   <li>A source is <b>healthy</b> when it is connected, its last message of any kind
 *       (heartbeats included) is younger than {@code staleThreshold}, and its last trade is
 *       younger than {@code priceStaleThreshold} (exactly a threshold already counts as stale).</li>
 *   <li>If the active source is unhealthy and the other one is healthy, switch immediately.</li>
 *   <li>While on the backup, switch back to the primary once the primary has been
 *       continuously healthy for {@code recoveryPeriod}; any unhealthy moment restarts that timer.</li>
 *   <li>If neither is healthy the state is STALE (some connection open) or DISCONNECTED.</li>
 * </ol>
 * The status is published whenever it changes and at least every {@code STATUS_INTERVAL}.
 * The rules only read the injected {@link Clock}, so tests drive them with a mutable clock
 * and direct {@code check()} calls - no sleeping.
 */
public class FeedManager implements SmartLifecycle, TickListener {

    /** Creates a client for one exchange; lets tests substitute fakes. */
    @FunctionalInterface
    public interface ClientFactory {
        PriceFeedClient create(FeedMessageParser parser, TickListener listener);
    }

    static final Duration STATUS_INTERVAL = Duration.ofSeconds(5);
    private static final Logger log = LoggerFactory.getLogger(FeedManager.class);

    private final PriceFeedClient primary;
    private final PriceFeedClient backup;
    private final TickPublisher tickPublisher;
    private final FeedStatusPublisher statusPublisher;
    private final Clock clock;
    private final Duration staleThreshold;
    private final Duration priceStaleThreshold;
    private final Duration recoveryPeriod;

    private volatile PriceFeedClient active;
    private volatile FeedStatus lastStatus;
    private volatile Instant lastTickPublishedAt;
    private Instant lastStatusPublishedAt;
    private Instant primaryHealthySince;
    private ScheduledExecutorService scheduler;
    private volatile boolean running;

    public FeedManager(ClientFactory factory, FeedMessageParser primaryParser, FeedMessageParser backupParser,
                       TickPublisher tickPublisher, FeedStatusPublisher statusPublisher, Clock clock,
                       Duration staleThreshold, Duration priceStaleThreshold, Duration recoveryPeriod) {
        this.tickPublisher = tickPublisher;
        this.statusPublisher = statusPublisher;
        this.clock = clock;
        this.staleThreshold = staleThreshold;
        this.priceStaleThreshold = priceStaleThreshold;
        this.recoveryPeriod = recoveryPeriod;
        // The clients only call back after start(), so handing out "this" here is safe.
        this.primary = factory.create(primaryParser, this);
        this.backup = factory.create(backupParser, this);
        this.active = primary;
    }

    /** Called by the clients' threads for every parsed tick. */
    @Override
    public void onTick(String source, PriceTick tick) {
        if (source.equals(active.sourceName())) {
            tickPublisher.publish(tick);
            lastTickPublishedAt = clock.instant();
        }
    }

    /**
     * When this process last received a trade from the active source and handed it to Kafka
     * (this machine's clock, so an exchange with a skewed clock cannot hide a stall).
     */
    public Optional<Instant> lastTickPublishedAt() {
        return Optional.ofNullable(lastTickPublishedAt);
    }

    /** Evaluate health and switch sources if needed. Safe to call from tests at any time. */
    public synchronized void check() {
        Instant now = clock.instant();
        boolean primaryHealthy = healthy(primary, now);
        boolean backupHealthy = healthy(backup, now);

        if (primaryHealthy) {
            if (primaryHealthySince == null) {
                primaryHealthySince = now;
            }
        } else {
            primaryHealthySince = null;
        }

        PriceFeedClient other = active == primary ? backup : primary;
        boolean activeHealthy = active == primary ? primaryHealthy : backupHealthy;
        boolean otherHealthy = active == primary ? backupHealthy : primaryHealthy;

        if (!activeHealthy && otherHealthy) {
            switchTo(other, active.sourceName() + " is stale");
        } else if (active == backup && primaryHealthy
                && Duration.between(primaryHealthySince, now).compareTo(recoveryPeriod) >= 0) {
            switchTo(primary, "primary healthy for " + recoveryPeriod.toSeconds() + "s");
        }

        State state;
        if (primaryHealthy || backupHealthy) {
            state = State.LIVE;
        } else if (primary.isConnected() || backup.isConnected()) {
            state = State.STALE;
        } else {
            state = State.DISCONNECTED;
        }
        FeedStatus status = new FeedStatus(active.sourceName(), state, active.lastTickAt().orElse(null), now);
        boolean changed = !status.sameAs(lastStatus);
        if (changed || lastStatusPublishedAt == null
                || Duration.between(lastStatusPublishedAt, now).compareTo(STATUS_INTERVAL) >= 0) {
            if (changed && lastStatus != null && lastStatus.state() != state) {
                log.warn("Feed state {} -> {} (active: {})", lastStatus.state(), state, active.sourceName());
            }
            statusPublisher.publish(status);
            lastStatusPublishedAt = now;
        }
        lastStatus = status;
    }

    /**
     * Connected, heard from within {@code staleThreshold} (any message - heartbeats keep a quiet
     * market "alive"), and a real trade within {@code priceStaleThreshold} (a live socket that
     * never delivers prices is useless). Exactly at a threshold counts as stale.
     */
    private boolean healthy(PriceFeedClient client, Instant now) {
        return client.isConnected()
                && younger(client.lastMessageAt(), now, staleThreshold)
                && younger(client.lastTickAt(), now, priceStaleThreshold);
    }

    private static boolean younger(java.util.Optional<Instant> at, Instant now, Duration limit) {
        return at.map(t -> Duration.between(t, now).compareTo(limit) < 0).orElse(false);
    }

    private void switchTo(PriceFeedClient target, String reason) {
        log.warn("Switching price source {} -> {} ({})", active.sourceName(), target.sourceName(), reason);
        active = target;
    }

    public FeedStatus currentStatus() {
        return lastStatus;
    }

    public String activeSource() {
        return active.sourceName();
    }

    /** Both clients, primary first (used by the chaos endpoint). */
    public List<PriceFeedClient> clients() {
        return List.of(primary, backup);
    }

    // ---- lifecycle ----

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        primary.start();
        backup.start();
        scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("feed-manager").daemon().factory());
        scheduler.scheduleAtFixedRate(() -> {
            try {
                check();
            } catch (RuntimeException e) {
                log.error("Feed check failed", e); // never let the schedule die
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        primary.stop();
        backup.stop();
        statusPublisher.shutdown();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
