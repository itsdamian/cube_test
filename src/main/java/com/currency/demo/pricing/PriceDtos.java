package com.currency.demo.pricing;

import com.currency.demo.feed.FeedStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** JSON shapes of the /api/prices endpoints (the frontend's contract; see contracts/api-samples). */
public final class PriceDtos {

    private PriceDtos() {
    }

    /** GET /api/prices/latest. {@code status} is null until a feed status has been seen. */
    public record LatestPrice(String pair, BigDecimal price, String source, Instant eventTime, FeedStatus status) {
    }

    public record PricePoint(Instant eventTime, BigDecimal price, String source) {
    }

    /** GET /api/prices/history - one page; pass {@code nextCursor} back to get the next one (null = last page). */
    public record HistoryPage(List<PricePoint> items, String nextCursor) {
    }

    /** One downsampled point: the last price inside [bucketStart, bucketStart + bucket width). */
    public record TrendPoint(Instant bucketStart, Instant eventTime, BigDecimal price) {
    }

    public record Trend(Instant from, Instant to, long bucketSeconds, List<TrendPoint> points) {
    }
}
