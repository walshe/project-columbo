# Story 003 — Tasks

## Phase 1 — Schema

- [ ] Create Flyway migration V3__create_signal_state.sql
- [ ] Define ENUM indicator_type ('SUPERTREND')
- [ ] Define ENUM trend_state ('BULLISH', 'BEARISH')
- [ ] Define ENUM signal_event ('NONE', 'BULLISH_REVERSAL', 'BEARISH_REVERSAL')
- [ ] Create `signal_state` table
- [ ] Add unique constraint (asset_id, timeframe, indicator_type, close_time)
- [ ] Start application and verify Flyway runs
- [ ] Inspect schema manually

---

## Phase 2 — Domain & Mapping

- [ ] Create SignalState entity
- [ ] Map timeframe ENUM correctly (NAMED_ENUM)
- [ ] Map indicator_type ENUM correctly (NAMED_ENUM)
- [ ] Map trend_state ENUM correctly (NAMED_ENUM)
- [ ] Map signal_event ENUM correctly (NAMED_ENUM)
- [ ] Create SignalStateRepository
- [ ] Implement findLatestByAssetIdAndTimeframeAndIndicatorType(...)
- [ ] Implement findAllByAssetIdAndTimeframeAndIndicatorTypeOrderByCloseTimeAsc(...)
- [ ] Add basic repository test

---

## Phase 3 — SignalStateCalculator (Pure Engine)

- [ ] Create SignalStateCalculator class
- [ ] Implement oldest → newest sequential processing
- [ ] Implement first-row initialization rule
- [ ] Implement DOWN → UP → BULLISH_REVERSAL logic
- [ ] Implement UP → DOWN → BEARISH_REVERSAL logic
- [ ] Implement no-change → event NONE logic
- [ ] Ensure indicator_type is always SUPERTREND
- [ ] Ensure no candle access in this layer
- [ ] Add full unit test suite for calculator

---

## Phase 4 — Finalized Filtering

- [ ] Fetch SuperTrend rows ordered ascending
- [ ] Implement UTC midnight boundary calculation
- [ ] Filter out non-finalized rows
- [ ] Unit test finalized filter logic

---

## Phase 5 — Incremental Processing

- [ ] Fetch latest signal_state close_time for:
      asset_id + timeframe + indicator_type
- [ ] Fetch SuperTrend rows strictly after that
- [ ] Retrieve previous direction for transition continuity
- [ ] Implement fallback for first-run (no previous state)
- [ ] Implement optional full recalculation mode
- [ ] Unit test incremental correctness

---

## Phase 6 — Persistence / Upsert Logic

- [ ] Implement INSERT ON CONFLICT logic
- [ ] Compare fields excluding created_at
- [ ] Log WARNING on revision
- [ ] Track inserted_count
- [ ] Track updated_count
- [ ] Track skipped_count
- [ ] Log per-asset summary

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