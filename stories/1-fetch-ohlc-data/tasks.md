# Story 001 — Tasks

## Phase 1 — Schema

- [ ] Create Flyway migration V1__create_asset_timeframe_candle.sql
- [ ] Define Postgres ENUM type timeframe ('1D')
- [ ] Create `asset` table with provider_id NOT NULL
- [ ] Create `candle` table with fields
- [ ] Add unique constraint (asset_id, timeframe, close_time)
- [ ] Start app and verify Flyway runs
- [ ] Inspect schema manually

---

## Phase 2 — Provider Layer

- [ ] Create CandleDto with open, high, low, close, open_time, close_time
- [ ] Create MarketDataProvider interface
- [ ] Implement CoinGeckoMarketDataProvider
- [ ] Configure demo API key property
- [ ] Fetch `/coins/{id}/ohlc` with days=365
- [ ] Normalize timestamp to UTC
- [ ] Respect API rate limits (delay between calls)

---

## Phase 3 — Ingestion Layer

- [ ] Create CandleIngestionService
- [ ] Inject provider & repository
- [ ] Fetch active assets
- [ ] Sort candles by close_time ascending
- [ ] Finalized candle filter
- [ ] Map DTO → entity
- [ ] Wrap asset ingestion in transaction

---

## Phase 4 — Upsert Logic

- [ ] Implement INSERT ON CONFLICT
- [ ] Compare fields excluding raw_payload & created_at
- [ ] Log warning on revision
- [ ] Track inserted_count
- [ ] Track updated_count
- [ ] Track skipped_count
- [ ] Log summary

---

## Phase 5 — Scheduler

- [ ] Add @Scheduled ingestion
- [ ] Externalize cron config
- [ ] Catch exceptions

---

## Phase 6 — Testing

- [ ] Unit test for finalized filtering
- [ ] Unit test for revision detection
- [ ] Provider parsing test
- [ ] Testcontainers integration test
- [ ] Run full test suite

---

## Phase 7 — Manual Validation

- [ ] Insert assets manually
- [ ] Run ingestion manually
- [ ] Inspect DB
- [ ] Run ingestion again
- [ ] Verify idempotency
