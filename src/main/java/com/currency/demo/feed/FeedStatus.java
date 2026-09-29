package com.currency.demo.feed;

import java.time.Instant;

/**
 * What the frontend shows about the price feed; published to the compacted topic
 * {@code btc.feed.status} whenever it changes and at least every 5 seconds.
 *
 * @param activeSource the exchange whose prices are currently published
 * @param state        LIVE, STALE (a connection is open but no fresh price) or DISCONNECTED (no connection)
 * @param lastTickAt   when the active source last delivered a price; {@code null} if never
 * @param reportedAt   when this status was produced
 */
public record FeedStatus(String activeSource, State state, Instant lastTickAt, Instant reportedAt) {

    public enum State {
        /** The active source is delivering fresh prices. */
        LIVE,
        /** No source has a fresh price, but at least one connection is open ("資料延遲"). */
        STALE,
        /** No source is connected ("已斷線"). */
        DISCONNECTED
    }

    /** Same content ignoring {@code reportedAt}; used to detect real changes. */
    boolean sameAs(FeedStatus other) {
        return other != null
                && activeSource.equals(other.activeSource)
                && state == other.state
                && java.util.Objects.equals(lastTickAt, other.lastTickAt);
    }
}
