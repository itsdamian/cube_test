package com.currency.demo.candle;

import com.currency.demo.pricing.PriceTick;
import com.currency.demo.pricing.TickOrder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Running state of one candle while its window is open (stored as JSON in the Streams state
 * store). Open/close keep the whole tick so ties are broken with {@link TickOrder}, making the
 * result independent of arrival order.
 */
public record CandleAccumulator(PriceTick first, PriceTick last, BigDecimal high, BigDecimal low, int count) {

    public static CandleAccumulator empty() {
        return new CandleAccumulator(null, null, null, null, 0);
    }

    public CandleAccumulator add(PriceTick tick) {
        if (count == 0) {
            return new CandleAccumulator(tick, tick, tick.price(), tick.price(), 1);
        }
        return new CandleAccumulator(
                TickOrder.COMPARATOR.compare(tick, first) < 0 ? tick : first,
                TickOrder.COMPARATOR.compare(tick, last) > 0 ? tick : last,
                tick.price().max(high),
                tick.price().min(low),
                count + 1);
    }

    public Candle toCandle(String pair, String interval, Instant openTime, Instant closeTime) {
        return new Candle(pair, interval, openTime, closeTime, first.price(), high, low, last.price(), count);
    }
}
