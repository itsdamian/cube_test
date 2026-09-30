# Exchange message fixtures

Captured from the real public WebSocket feeds on 2026-09-29 (trimmed where noted):

- Coinbase Exchange `wss://ws-feed.exchange.coinbase.com`, channels `ticker` + `heartbeat`
  (`error.json` is the real reply to the invalid channel name `heartbeats`).
- Kraken v2 `wss://ws.kraken.com/v2`, channel `trade` with `snapshot:false`.
  `trade-multi.json` merges three real trades into one message (Kraken batches trades);
  `status.json` is trimmed (maintenance notice removed); `subscribe-error.json` and
  `trade-snapshot.json` follow the documented v2 shapes.

Tests only read these files; they never connect to an exchange (requirement 18).

- open.er-api.com `GET /v6/latest/USD` (`fx/open-er-api-latest-usd.json`), captured 2026-09-30,
  unmodified.
