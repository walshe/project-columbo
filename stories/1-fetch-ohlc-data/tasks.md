# Story 001 — Tasks

## Schema

- [ ] Create Liquibase changeset for timeframe ENUM
- [ ] Create asset table
- [ ] Create candle table
- [ ] Add unique constraint (asset_id, timeframe, close_time)
- [ ] Run docker compose and verify schema

---

## Provider Layer

- [ ] Create MarketDataProvider interface
- [ ] Implement CoinGeckoMarketDataProvider
- [ ] Add configuration for API base URL
- [ ] Normalize timestamps to UTC

---

## Ingestion Layer

- [ ] Create CandleIngestionService
- [ ] Inject provider + repository
- [ ] Implement finalized candle filter
- [ ] Implement upsert logic
- [ ] Add revision WARNING logging
- [ ] Add summary logging

---

## Scheduler

- [ ] Add @Scheduled ingestion method
- [ ] Externalize cron to config

---

## Testing

- [ ] Add unit test for finalized filtering
- [ ] Add unit test for revision detection
- [ ] Add Testcontainers integration test
- [ ] Run full test suite

---

## Manual Validation

- [ ] Seed at least 2 assets
- [ ] Run ingestion manually
- [ ] Inspect DB
- [ ] Run ingestion again
- [ ] Verify idempotency
