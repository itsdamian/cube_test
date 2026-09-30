-- Price alerts (spec 13, 14, 14a). Single user, so no owner column.
CREATE TABLE price_alert (
    id                BIGSERIAL      PRIMARY KEY,
    pair              VARCHAR(16)    NOT NULL,
    direction         VARCHAR(5)     NOT NULL CHECK (direction IN ('ABOVE', 'BELOW')),
    threshold         NUMERIC(20, 8) NOT NULL CHECK (threshold > 0),
    created_at        TIMESTAMPTZ    NOT NULL,
    last_triggered_at TIMESTAMPTZ
);

-- Every time an alert fired. read_at IS NULL = not yet seen by the user (shown after reopening the page).
-- Deleting an alert deletes its history.
CREATE TABLE alert_event (
    id              BIGSERIAL      PRIMARY KEY,
    alert_id        BIGINT         NOT NULL REFERENCES price_alert (id) ON DELETE CASCADE,
    direction       VARCHAR(5)     NOT NULL,
    threshold       NUMERIC(20, 8) NOT NULL,
    triggered_price NUMERIC(20, 8) NOT NULL,
    triggered_at    TIMESTAMPTZ    NOT NULL,
    read_at         TIMESTAMPTZ
);

CREATE INDEX idx_alert_event_unread ON alert_event (triggered_at) WHERE read_at IS NULL;
