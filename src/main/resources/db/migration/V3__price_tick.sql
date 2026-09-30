-- Every received trade price (spec requirement 6). Kept for 30 days (RetentionJob, task 13).
-- event_id is unique so at-least-once Kafka delivery cannot create duplicates
-- (inserts use ON CONFLICT (event_id) DO NOTHING).
CREATE TABLE price_tick (
    id          BIGSERIAL      PRIMARY KEY,
    event_id    UUID           NOT NULL,
    pair        VARCHAR(16)    NOT NULL,
    price       NUMERIC(20, 8) NOT NULL,
    source      VARCHAR(16)    NOT NULL,
    event_time  TIMESTAMPTZ    NOT NULL,
    received_at TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uk_price_tick_event_id UNIQUE (event_id)
);

-- History / trend queries and candle cross-checks filter by pair and time range.
CREATE INDEX idx_price_tick_pair_event_time ON price_tick (pair, event_time);
