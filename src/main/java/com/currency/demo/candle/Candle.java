package com.currency.demo.candle;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A finalised OHLC candle for [openTime, closeTime) - the event on {@code btc.candles}.
 *
 * @param interval  "1m" or "5m"
 * @param tickCount number of ticks aggregated
 */
public record Candle(String pair, String interval, Instant openTime, Instant closeTime,
                     BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, int tickCount) {

    /** Kafka key: one key per candle, so a re-emitted candle is an upsert, not a new one. */
    public String key() {
        return pair + "|" + interval + "|" + openTime;
    }
}
