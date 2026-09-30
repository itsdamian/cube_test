-- AC4: every stored candle vs. the same aggregate computed from the stored ticks.
-- open/close use the canonical order (event_time, received_at, event_id) - the same rule as Kafka Streams.
WITH from_ticks AS (
    SELECT c.interval_code, c.open_time,
           (array_agg(t.price ORDER BY t.event_time, t.received_at, t.event_id))[1]                AS open,
           max(t.price)                                                                         AS high,
           min(t.price)                                                                         AS low,
           (array_agg(t.price ORDER BY t.event_time DESC, t.received_at DESC, t.event_id DESC))[1] AS close
    FROM candle c
    JOIN price_tick t ON t.pair = c.pair AND t.event_time >= c.open_time AND t.event_time < c.close_time
    WHERE c.open_time >= now() - interval '1 hour'
    GROUP BY c.interval_code, c.open_time
)
SELECT c.interval_code, c.open_time AT TIME ZONE 'Asia/Taipei' AS open_time_taipei,
       c.open, c.high, c.low, c.close,
       (c.open = f.open AND c.high = f.high AND c.low = f.low AND c.close = f.close) AS ohlc_matches
FROM candle c JOIN from_ticks f USING (interval_code, open_time)
ORDER BY c.interval_code, c.open_time;
