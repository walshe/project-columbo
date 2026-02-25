# Story 005 — Tasks

## Phase 1 — API Contract & DTOs

- [x] Create API base path `/api/v1`
- [x] Create DTO `SignalStateDto`
- [x] Create DTO `MarketPulseDto`
- [x] Create DTO for history response (either `MarketPulseDto` list or `MarketPulseHistoryDto`)
- [x] Create request enums (or reuse domain enums safely):
  - [x] `SignalSort` (LAST_FLIP_ASC / LAST_FLIP_DESC / ASSET_ASC)
- [x] Implement explicit mappers (Entity → DTO only)
- [x] Ensure controllers will not serialize entities directly

---

## Phase 2 — Repository Query Methods (Bulk, Deterministic)

### Active Assets
- [x] Add repository method to fetch active assets (symbols + ids)
      (projection preferred)

### Latest finalized signal state per asset
- [x] Implement bulk query to fetch latest finalized `signal_state` per asset
      for `(timeframe, indicator_type)` with `close_time < utcMidnightToday`
- [x] Implement bulk query to fetch latest flip per asset
      (`event != NONE`) for `(timeframe, indicator_type)` with `close_time < utcMidnightToday`

### Market pulse snapshots
- [x] Add repository method:
      `findTopByTimeframeAndIndicatorTypeOrderBySnapshotCloseTimeDesc(...)`

### Market pulse history
- [x] Add repository method to list snapshots ordered by `snapshotCloseTime ASC`
- [x] Add optional `from/to` filtering support

- [x] Add repository-level tests for the above queries

---

## Phase 3 — Time Model Helpers (Finalized Boundary + Days Since Flip)

- [x] Introduce injectable `TimeProvider` / `Clock` for UTC time
- [x] Implement helper: `utcMidnightToday()`
- [x] Implement `timeSinceFlipDays` computation (whole days, UTC)
- [x] Decide and implement behavior when flip is missing:
  - [x] return null (recommended) OR use earliest state close_time
- [x] Unit test time calculations with fixed clock

---

## Phase 4 — Service Layer (Query Services)

### SignalQueryService
- [x] Create `SignalQueryService`
- [x] Implement:
      `listSignals(timeframe, indicatorType, state?, sort?)`
- [x] Ensure it returns one row per ACTIVE asset
- [x] Ensure it uses only FINALIZED data
- [x] Apply filtering by state when provided
- [x] Apply deterministic sorting:
  - [x] LAST_FLIP_ASC
  - [x] LAST_FLIP_DESC
  - [x] ASSET_ASC
- [x] Compute `lastFlipTime` + `timeSinceFlipDays` server-side
- [x] Ensure no per-asset DB loops (no N+1 queries)

### MarketPulseQueryService
- [x] Create `MarketPulseQueryService`
- [x] Implement:
  - [x] `getLatestPulse(timeframe, indicatorType)`
  - [x] `getPulseHistory(timeframe, indicatorType, from?, to?)`

- [x] Add unit tests for service-level sorting and mapping

---

## Phase 5 — Controllers (Endpoints)

- [x] Create controller package for `/api/v1`

### GET /api/v1/signals
- [x] Implement endpoint:
      `/api/v1/signals`
- [x] Validate required params: timeframe + indicatorType
- [x] Support optional params: state, sort
- [x] Return 200 + list of `SignalStateDto`

### GET /api/v1/assets/by-state
- [x] Implement endpoint:
      `/api/v1/assets/by-state`
- [x] Validate required params: timeframe + indicatorType + state
- [x] Return 200 + list of asset symbols

### GET /api/v1/market-pulse
- [x] Implement endpoint:
      `/api/v1/market-pulse`
- [x] Validate required params: timeframe + indicatorType
- [x] Return 200 + `MarketPulseDto`

### GET /api/v1/market-pulse/history
- [x] Implement endpoint:
      `/api/v1/market-pulse/history`
- [x] Validate required params: timeframe + indicatorType
- [x] Support optional params: from, to (ISO-8601)
- [x] Return 200 + ordered list (oldest → newest)

---

## Phase 6 — Validation & Error Handling

- [x] Add consistent enum validation for timeframe/indicatorType/state/sort
- [x] Return 400 for invalid inputs
- [x] Return 200 + empty list when no data found
- [x] Add `@ControllerAdvice` (or equivalent) for uniform 400 responses
- [x] Add tests verifying 400 behavior for invalid params

---

## Phase 7 — Performance & Index Check

- [x] Confirm index exists for `signal_state` suitable for "latest per asset" query
- [x] Confirm index exists for `market_breadth_snapshot` latest + range queries
- [x] Ensure no N+1 queries (verify with logs if needed)

---

## Phase 8 — Integration Tests (Testcontainers)

- [x] Seed:
  - [x] active assets
  - [x] signal_state rows for multiple days
  - [x] market_breadth_snapshot rows for multiple days

- [x] Integration test: `/api/v1/signals`
- [x] Integration test: `/api/v1/signals` with state filter
- [x] Integration test: `/api/v1/signals` with sort LAST_FLIP_DESC
- [x] Integration test: `/api/v1/assets/by-state`
- [x] Integration test: `/api/v1/market-pulse`
- [x] Integration test: `/api/v1/market-pulse/history` order correctness
- [x] Integration test: invalid params return 400

---

## Phase 9 — Manual Validation

- [x] Run app + DB
- [x] Verify with curl:
  - [x] `/api/v1/signals?timeframe=D1&indicatorType=SUPERTREND`
  - [x] `/api/v1/signals?timeframe=D1&indicatorType=SUPERTREND&state=BULLISH&sort=LAST_FLIP_DESC`
  - [x] `/api/v1/assets/by-state?timeframe=D1&indicatorType=SUPERTREND&state=BEARISH`
  - [x] `/api/v1/market-pulse?timeframe=D1&indicatorType=SUPERTREND`
  - [x] `/api/v1/market-pulse/history?timeframe=D1&indicatorType=SUPERTREND`
- [x] Confirm outputs match DB and finalized-day rules