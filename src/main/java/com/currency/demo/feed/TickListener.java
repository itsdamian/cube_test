package com.currency.demo.feed;

import com.currency.demo.pricing.PriceTick;

/** Receives every tick a feed client parses; {@link FeedManager} decides whether to publish it. */
@FunctionalInterface
public interface TickListener {

    void onTick(String source, PriceTick tick);
}
