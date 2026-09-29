-- Fiat currencies shown in the BTC conversion table.
-- Flyway runs every V<n>__*.sql file once, in order, and records it in
-- flyway_schema_history, so the schema is versioned together with the code.
CREATE TABLE currency (
    id         BIGSERIAL    PRIMARY KEY,
    code       VARCHAR(3)   NOT NULL,
    name       VARCHAR(50)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_currency_code UNIQUE (code)
);
