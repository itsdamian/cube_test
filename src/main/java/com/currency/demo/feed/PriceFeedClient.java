package com.currency.demo.feed;

import java.time.Instant;
import java.util.Optional;

/**
 * One exchange connection as seen by {@link FeedManager}: is it up, and when did it last
 * deliver a price? The real implementation is {@link WebSocketPriceFeedClient}; failover
 * tests use a hand-controlled fake.
 */
public interface PriceFeedClient {

    /** Source name, e.g. {@code coinbase}. */
    String sourceName();

    void start();

    void stop();

    boolean isRunning();

    /** True while a WebSocket session is open. */
    boolean isConnected();

    /** When the last tick (not heartbeat) was received, if any. */
    Optional<Instant> lastTickAt();

    /** Fault injection for manual acceptance tests (chaos profile only). */
    void block(BlockMode mode);

    void unblock();

    BlockMode blockMode();

    enum BlockMode {
        /** Normal operation. */
        NONE,
        /** Drop the connection and treat every reconnect attempt as failed. */
        DISCONNECT,
        /** Keep the connection open but ignore every message (simulates a half-open socket). */
        SILENT
    }
}
