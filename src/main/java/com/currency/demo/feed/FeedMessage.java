package com.currency.demo.feed;

import com.currency.demo.pricing.PriceTick;

import java.util.List;

/**
 * The result of parsing one WebSocket text message from an exchange.
 *
 * <p>A {@code sealed} interface lists every possible outcome, so a {@code switch} over a
 * {@code FeedMessage} is checked by the compiler for completeness. Every variant except
 * {@link Invalid} counts as "the connection is alive" for the idle watchdog; only
 * {@link Trades} carries prices.
 */
public sealed interface FeedMessage {

    /** Prices to publish. Empty for every other kind of message. */
    default List<PriceTick> ticks() {
        return List.of();
    }

    /** One or more trades (Kraken batches several trades in one message). */
    record Trades(List<PriceTick> ticks) implements FeedMessage {
        public Trades {
            ticks = List.copyOf(ticks);
        }
    }

    /** Exchange keep-alive (about once per second on both exchanges). */
    record Heartbeat() implements FeedMessage {
    }

    /** Subscription acks, system status, snapshots we ignore... valid but nothing to publish. */
    record Control(String description) implements FeedMessage {
    }

    /** The exchange reported an error (e.g. a rejected subscription). */
    record ExchangeError(String message) implements FeedMessage {
    }

    /** Not JSON, or not a shape we understand. Logged and ignored; never throws. */
    record Invalid(String reason) implements FeedMessage {
    }
}
