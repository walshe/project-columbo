# Story 002 — Tasks

## Phase 1 — Schema

- [x] Create Flyway migration V2__create_indicator_supertrend.sql
- [x] Define ENUM supertrend_direction ('UP', 'DOWN')
- [x] Create `indicator_supertrend` table
- [x] Add unique constraint (asset_id, timeframe, close_time)
- [x] Map ENUM types correctly (NAMED_ENUM)
- [x] Start app and verify Flyway runs
- [x] Inspect schema manually

---

## Phase 2 — Domain & Mapping

- [x] Create SuperTrendIndicator entity
- [x] Map timeframe ENUM correctly
- [x] Map direction ENUM correctly
- [x] Use BigDecimal for numeric fields
- [x] Add SuperTrendRepository
- [x] Implement findLatestByAssetIdAndTimeframe(...)
- [x] Add basic repository test

---

## Phase 3 — SuperTrend Calculator (Pure Engine)

- [x] Create SuperTrendCalculator class
- [x] Implement True Range (TR)
- [x] Implement initial ATR calculation
- [x] Implement Wilder smoothing formula
- [x] Implement basic upper/lower bands
- [x] Implement final band logic
- [x] Implement SuperTrend switching logic
- [x] Implement direction derivation
- [x] Ensure oldest → newest sequential processing
- [x] Ensure no floating point usage (BigDecimal only)
- [x] Add full unit test suite for calculator

---

## Phase 4 — Finalized Candle Filtering

- [x] Fetch candles ordered ascending
- [x] Implement UTC midnight boundary calculation
- [x] Filter out non-finalized candles
- [x] Unit test finalized filter logic

---

## Phase 5 — Incremental Strategy

- [x] Fetch latest stored SuperTrend close_time
- [x] Implement incremental continuation logic
- [x] Recompute from (lastIndex - atrLength - 1)
- [x] Implement optional full recalculation mode
- [x] Unit test incremental correctness

---

## Phase 6 — Persistence / Upsert Logic

- [x] Implement INSERT ON CONFLICT logic
- [x] Compare fields excluding created_at
- [x] Log WARNING on revision
- [x] Track inserted_count
- [x] Track updated_count
- [x] Track skipped_count
- [x] Log per-asset summary

---

## Phase 7 — Service Layer

- [x] Create SuperTrendService
- [x] Inject repository + calculator
- [x] Fetch active assets
- [x] Fetch finalized candles
- [x] Call calculator
- [x] Persist results
- [x] Wrap per-asset calculation in transaction
- [x] Log summary

---

## Phase 8 — Configuration

- [ ] Add properties:
      app.indicators.supertrend.atr-length
      app.indicators.supertrend.multiplier
- [ ] Inject into service
- [ ] Unit test property binding

---

## Phase 9 — Scheduler (Optional)

- [ ] Add @Scheduled method
- [ ] Externalize cron config
- [ ] Catch and log exceptions

---

## Phase 10 — Integration Testing

- [x] Seed 30+ finalized candles (Testcontainers)
- [x] Run SuperTrend calculation
- [x] Assert rows created
- [x] Run again
- [x] Assert idempotency
- [x] Simulate modified candle
- [x] Assert revision update path

---

## Phase 11 — Manual Validation

- [ ] Seed BTCUSDT candles
- [ ] Run calculation manually
- [ ] Inspect DB values
- [ ] Validate direction switching
- [ ] Confirm deterministic behavior