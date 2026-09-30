package com.currency.demo.pricing;

import com.currency.demo.pricing.PriceDtos.HistoryPage;
import com.currency.demo.pricing.PriceDtos.LatestPrice;
import com.currency.demo.pricing.PriceDtos.PricePoint;
import com.currency.demo.pricing.PriceDtos.Trend;
import com.currency.demo.pricing.PriceDtos.TrendPoint;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Validation and paging rules for the price read API. */
@Service
public class PriceQueryService {

    public static final int DEFAULT_LIMIT = 1_000;
    public static final int MAX_LIMIT = 5_000;
    public static final int DEFAULT_POINTS = 300;
    public static final int MAX_POINTS = 1_000;

    private final PriceQueryRepository repository;
    private final FeedStatusTracker statusTracker;

    public PriceQueryService(PriceQueryRepository repository, FeedStatusTracker statusTracker) {
        this.repository = repository;
        this.statusTracker = statusTracker;
    }

    public LatestPrice latest() {
        PriceQueryRepository.Row row = repository.latest(PriceTick.BTC_USD).orElseThrow(NoPriceYetException::new);
        return new LatestPrice(PriceTick.BTC_USD, row.price(), row.source(), row.eventTime(),
                statusTracker.current().orElse(null));
    }

    /** Ticks in [from, to), ordered by (event_time, id), at most {@code limit} per page. */
    public HistoryPage history(Instant from, Instant to, int limit, String cursor) {
        requireRange(from, to);
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidQueryException("limit must be between 1 and " + MAX_LIMIT);
        }
        HistoryCursor after = cursor == null || cursor.isBlank()
                ? new HistoryCursor(from.minusNanos(1_000), 0)   // before the first possible row
                : HistoryCursor.decode(cursor);
        // Ask for one extra row: if it exists there is a next page.
        List<PriceQueryRepository.Row> rows =
                repository.page(PriceTick.BTC_USD, from, to, after.eventTime(), after.id(), limit + 1);
        boolean more = rows.size() > limit;
        List<PriceQueryRepository.Row> page = more ? rows.subList(0, limit) : rows;
        String next = more ? new HistoryCursor(page.getLast().eventTime(), page.getLast().id()).encode() : null;
        return new HistoryPage(page.stream().map(r -> new PricePoint(r.eventTime(), r.price(), r.source(), r.receivedAt(), r.eventId()))
                        .toList(),
                next);
    }

    /** At most {@code points} downsampled points covering [from, to). */
    public Trend trend(Instant from, Instant to, int points) {
        requireRange(from, to);
        if (points < 1 || points > MAX_POINTS) {
            throw new InvalidQueryException("points must be between 1 and " + MAX_POINTS);
        }
        // Round the bucket width up to whole seconds so points * width always covers the range.
        long rangeSeconds = Math.max(1, Duration.between(from, to).toSeconds()
                + (Duration.between(from, to).toNanosPart() > 0 ? 1 : 0));
        long bucketSeconds = Math.max(1, (rangeSeconds + points - 1) / points);
        List<TrendPoint> result = repository.trend(PriceTick.BTC_USD, from, to, Duration.ofSeconds(bucketSeconds))
                .stream().map(r -> new TrendPoint(r.bucketStart(), r.eventTime(), r.price())).toList();
        return new Trend(from, to, bucketSeconds, result);
    }

    private static void requireRange(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new InvalidQueryException("from must be before to");
        }
    }
}
