# Story 003 — Tasks

## Phase 1 — Schema

- [x] Create Flyway migration V3__create_signal_state.sql
- [x] Define ENUM indicator_type ('SUPERTREND')
- [x] Define ENUM trend_state ('BULLISH', 'BEARISH')
- [x] Define ENUM signal_event ('NONE', 'BULLISH_REVERSAL', 'BEARISH_REVERSAL')
- [x] Create `signal_state` table
- [x] Add unique constraint (asset_id, timeframe, indicator_type, close_time)
- [x] Start application and verify Flyway runs
- [x] Inspect schema manually

---

## Phase 2 — Domain & Mapping

- [x] Create SignalState entity
- [x] Map timeframe ENUM correctly (NAMED_ENUM)
- [x] Map indicator_type ENUM correctly (NAMED_ENUM)
- [x] Map trend_state ENUM correctly (NAMED_ENUM)
- [x] Map signal_event ENUM correctly (NAMED_ENUM)
- [x] Create SignalStateRepository
- [x] Implement findLatestByAssetIdAndTimeframeAndIndicatorType(...)
- [x] Implement findAllByAssetIdAndTimeframeAndIndicatorTypeOrderByCloseTimeAsc(...)
- [/] Add basic repository test (Created, but Docker issues prevent execution in this environment)

---

## Phase 3 — SignalStateCalculator (Pure Engine)

- [x] Create SignalStateCalculator class
- [x] Implement oldest → newest sequential processing
- [x] Implement first-row initialization rule
- [x] Implement DOWN → UP → BULLISH_REVERSAL logic
- [x] Implement UP → DOWN → BEARISH_REVERSAL logic
- [x] Implement no-change → event NONE logic
- [x] Ensure indicator_type is always SUPERTREND
- [x] Ensure no candle access in this layer
- [x] Add full unit test suite for calculator

---

## Phase 4 — Finalized Filtering

- [x] Fetch SuperTrend rows ordered ascending
- [x] Implement UTC midnight boundary calculation
- [x] Filter out non-finalized rows
- [x] Unit test finalized filter logic

---

## Phase 5 — Incremental Processing

- [x] Fetch latest signal_state close_time for:
      asset_id + timeframe + indicator_type
- [x] Fetch SuperTrend rows strictly after that
- [x] Retrieve previous direction for transition continuity
- [x] Implement fallback for first-run (no previous state)
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

- [ ] Create SignalStateService
- [ ] Inject repository + calculator
- [ ] Fetch active assets
- [ ] Fetch finalized SuperTrend rows
- [ ] Apply incremental logic
- [ ] Call calculator
- [ ] Persist results with indicator_type = SUPERTREND
- [ ] Wrap per-asset execution in transaction
- [ ] Log summary

---

## Phase 8 — Scheduler (Optional)

- [ ] Add @Scheduled detectDaily() method
- [ ] Externalize cron config
- [ ] Catch and log exceptions

---

## Phase 9 — Integration Testing (Testcontainers)

- [ ] Seed SuperTrend rows
- [ ] Run detection
- [ ] Assert signal_state rows created
- [ ] Run again
- [ ] Assert idempotency
- [ ] Modify direction
- [ ] Assert revision update path
- [ ] Ensure no candle dependency exists

---

## Phase 10 — Manual Validation

- [ ] Seed BTCUSDT SuperTrend history
- [ ] Run detection manually
- [ ] Inspect DB
- [ ] Validate reversal events match direction flips
- [ ] Confirm one row per indicator per finalized SuperTrend row
- [ ] Confirm deterministic behavior