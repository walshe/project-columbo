# Story 001 — Tasks

## Phase 1 — Schema

- [x] Create Flyway migration V1__create_asset_timeframe_candle.sql
- [x] Define ENUM timeframe ('1D')
- [x] Define ENUM provider ('BINANCE')
- [x] Create `asset` table with provider ENUM
- [x] Add unique constraint (symbol, provider)
- [x] Create `candle` table with provider ENUM as source
- [x] Add unique constraint (asset_id, timeframe, close_time)
- [x] Start app and verify Flyway runs
- [x] Inspect schema manually

---

## Phase 2 — Provider Layer (Binance)


- [x] Create CandleDto
- [x] Use BigDecimal for numeric fields
- [x] Create MarketDataProvider interface
- [x] Implement BinanceMarketDataProvider
- [x] Implement symbol normalization rule
- [x] Configure base URL
- [x] Call /api/v3/klines with interval=1d
- [x] Map openTime → open_time (UTC)
- [x] Map closeTime → close_time (UTC)
- [x] Parse numeric fields safely
- [x] Unit test provider parsing
- [x] Unit test symbol normalization

---

## Phase 3 — Ingestion Layer

- [x] Create CandleIngestionService
- [x] Inject provider & repository
- [x] Fetch active assets
- [x] Sort candles by close_time ascending
- [x] Implement finalized candle filter (UTC boundary)
- [x] Map DTO → entity
- [x] Wrap asset ingestion in transaction

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

- [x] Unit test finalized filtering
- [ ] Unit test revision detection
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