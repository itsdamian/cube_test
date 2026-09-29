package com.currency.demo.feed;

import com.currency.demo.pricing.PriceTick;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Test double for one exchange connection; the test decides when it is connected and ticks. */
final class FakeFeedClient implements PriceFeedClient {

    private final String name;
    private final TickListener listener;
    private final Clock clock;
    private volatile boolean connected = true;
    private volatile Instant lastTickAt;
    private volatile Instant lastMessageAt;
    private volatile boolean running;
    private volatile BlockMode blockMode = BlockMode.NONE;

    FakeFeedClient(String name, TickListener listener, Clock clock) {
        this.name = name;
        this.listener = listener;
        this.clock = clock;
    }

    /** Deliver one tick "now" (if connected and not blocked), exactly like the real client. */
    PriceTick tick() {
        Instant now = clock.instant();
        PriceTick tick = new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD, new BigDecimal("84000.00"), name, now, now);
        if (connected && blockMode == BlockMode.NONE) {
            lastTickAt = now;
            lastMessageAt = now;
            listener.onTick(name, tick);
        }
        return tick;
    }

    /** A heartbeat "now": proves the connection is alive, carries no price. */
    void heartbeat() {
        if (connected && blockMode == BlockMode.NONE) {
            lastMessageAt = clock.instant();
        }
    }

    void connected(boolean value) {
        connected = value;
    }

    @Override
    public String sourceName() {
        return name;
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isConnected() {
        return connected && blockMode != BlockMode.DISCONNECT;
    }

    @Override
    public Optional<Instant> lastTickAt() {
        return Optional.ofNullable(lastTickAt);
    }

    @Override
    public Optional<Instant> lastMessageAt() {
        return Optional.ofNullable(lastMessageAt);
    }

    @Override
    public void block(BlockMode mode) {
        blockMode = mode;
    }

    @Override
    public void unblock() {
        blockMode = BlockMode.NONE;
    }

    @Override
    public BlockMode blockMode() {
        return blockMode;
    }
}
