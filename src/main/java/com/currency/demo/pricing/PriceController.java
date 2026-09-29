package com.currency.demo.pricing;

import com.currency.demo.pricing.PriceDtos.HistoryPage;
import com.currency.demo.pricing.PriceDtos.LatestPrice;
import com.currency.demo.pricing.PriceDtos.Trend;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * BTC-USD price read API. Times are ISO-8601 instants (e.g. {@code 2026-09-29T08:00:00Z});
 * ranges are half-open [from, to). Without from/to the last 15 minutes are used.
 */
@RestController
@RequestMapping("/api/prices")
public class PriceController {

    static final Duration DEFAULT_RANGE = Duration.ofMinutes(15);

    private final PriceQueryService service;
    private final Clock clock;

    public PriceController(PriceQueryService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/latest")
    public LatestPrice latest() {
        return service.latest();
    }

    @GetMapping("/history")
    public HistoryPage history(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "" + PriceQueryService.DEFAULT_LIMIT) int limit,
            @RequestParam(required = false) String cursor) {
        Instant end = to != null ? to : clock.instant();
        return service.history(from != null ? from : end.minus(DEFAULT_RANGE), end, limit, cursor);
    }

    @GetMapping("/trend")
    public Trend trend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "" + PriceQueryService.DEFAULT_POINTS) int points) {
        Instant end = to != null ? to : clock.instant();
        return service.trend(from != null ? from : end.minus(DEFAULT_RANGE), end, points);
    }
}
