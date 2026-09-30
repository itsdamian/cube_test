-- Finalised OHLC candles from Kafka Streams (spec requirement 7). Not subject to the
-- 30-day tick retention. One row per (pair, interval, open_time); re-delivered candles upsert.
-- ("interval" is avoided as a column name because it is also a PostgreSQL type keyword.)
CREATE TABLE candle (
    pair          VARCHAR(16)    NOT NULL,
    interval_code VARCHAR(4)     NOT NULL,
    open_time     TIMESTAMPTZ    NOT NULL,
    close_time    TIMESTAMPTZ    NOT NULL,
    open          NUMERIC(20, 8) NOT NULL,
    high          NUMERIC(20, 8) NOT NULL,
    low           NUMERIC(20, 8) NOT NULL,
    close         NUMERIC(20, 8) NOT NULL,
    tick_count    INTEGER        NOT NULL,
    CONSTRAINT pk_candle PRIMARY KEY (pair, interval_code, open_time)
);
