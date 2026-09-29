package com.currency.demo.config;

/** Kafka topic names used across the app (one place, so producers and consumers agree). */
public final class Topics {

    /** Every received trade price ({@code PriceTick}); key = pair so one pair stays in one partition, in order. */
    public static final String PRICE_TICKS = "btc.price.ticks";
    /** Finalised OHLC candles produced by Kafka Streams. */
    public static final String CANDLES = "btc.candles";
    /** Price alerts that fired. */
    public static final String ALERTS_TRIGGERED = "btc.alerts.triggered";
    /** Latest feed status (active source, LIVE/STALE/DISCONNECTED); compacted - only the latest value matters. */
    public static final String FEED_STATUS = "btc.feed.status";

    private Topics() {
    }
}
