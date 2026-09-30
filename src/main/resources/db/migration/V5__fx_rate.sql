-- Latest fiat exchange rates (units of the currency per 1 USD), refreshed from open.er-api.com.
-- Only the most recent value per currency is kept; it survives restarts (spec requirement 3).
CREATE TABLE fx_rate (
    code                VARCHAR(3)     PRIMARY KEY,
    rate_per_usd        NUMERIC(20, 8) NOT NULL,
    provider_updated_at TIMESTAMPTZ    NOT NULL,
    fetched_at          TIMESTAMPTZ    NOT NULL
);
