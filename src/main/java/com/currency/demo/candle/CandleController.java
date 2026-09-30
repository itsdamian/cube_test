package com.currency.demo.candle;

import com.currency.demo.pricing.InvalidQueryException;
import com.currency.demo.pricing.PriceTick;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * GET /api/candles?interval=1m|5m&from&to - candles whose open time is in [from, to).
 * Without from/to: the last 120 candles' worth of time. At most 5,000 candles per request.
 */
@RestController
@RequestMapping("/api/candles")
public class CandleController {

    static final int MAX_CANDLES = 5_000;
    static final int DEFAULT_CANDLES = 120;
    private static final Map<String, Duration> INTERVALS = Map.of(
            CandleTopology.ONE_MINUTE.name(), CandleTopology.ONE_MINUTE.size(),
            CandleTopology.FIVE_MINUTES.name(), CandleTopology.FIVE_MINUTES.size());

    private final CandleRepository repository;
    private final Clock clock;

    public CandleController(CandleRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @GetMapping
    public List<Candle> candles(
            @RequestParam(defaultValue = "1m") String interval,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Duration size = INTERVALS.get(interval);
        if (size == null) {
            throw new InvalidQueryException("interval must be one of " + INTERVALS.keySet());
        }
        Instant end = to != null ? to : clock.instant();
        Instant start = from != null ? from : end.minus(size.multipliedBy(DEFAULT_CANDLES));
        if (!start.isBefore(end)) {
            throw new InvalidQueryException("from must be before to");
        }
        if (Duration.between(start, end).dividedBy(size) > MAX_CANDLES) {
            throw new InvalidQueryException("range too large: at most " + MAX_CANDLES + " candles per request");
        }
        return repository.find(PriceTick.BTC_USD, interval, start, end);
    }
}
