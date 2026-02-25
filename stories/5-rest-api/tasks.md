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
- [ ] Create `SignalQueryService`
- [ ] Implement:
      `listSignals(timeframe, indicatorType, state?, sort?)`
- [ ] Ensure it returns one row per ACTIVE asset
- [ ] Ensure it uses only FINALIZED data
- [ ] Apply filtering by state when provided
- [ ] Apply deterministic sorting:
  - [ ] LAST_FLIP_ASC
  - [ ] LAST_FLIP_DESC
  - [ ] ASSET_ASC
- [ ] Compute `lastFlipTime` + `timeSinceFlipDays` server-side
- [ ] Ensure no per-asset DB loops (no N+1 queries)

### MarketPulseQueryService
- [ ] Create `MarketPulseQueryService`
- [ ] Implement:
  - [ ] `getLatestPulse(timeframe, indicatorType)`
  - [ ] `getPulseHistory(timeframe, indicatorType, from?, to?)`

- [ ] Add unit tests for service-level sorting and mapping

---

## Phase 5 — Controllers (Endpoints)

- [ ] Create controller package for `/api/v1`

### GET /api/v1/signals
- [ ] Implement endpoint:
      `/api/v1/signals`
- [ ] Validate required params: timeframe + indicatorType
- [ ] Support optional params: state, sort
- [ ] Return 200 + list of `SignalStateDto`

### GET /api/v1/assets/by-state
- [ ] Implement endpoint:
      `/api/v1/assets/by-state`
- [ ] Validate required params: timeframe + indicatorType + state
- [ ] Return 200 + list of asset symbols

### GET /api/v1/market-pulse
- [ ] Implement endpoint:
      `/api/v1/market-pulse`
- [ ] Validate required params: timeframe + indicatorType
- [ ] Return 200 + `MarketPulseDto`

### GET /api/v1/market-pulse/history
- [ ] Implement endpoint:
      `/api/v1/market-pulse/history`
- [ ] Validate required params: timeframe + indicatorType
- [ ] Support optional params: from, to (ISO-8601)
- [ ] Return 200 + ordered list (oldest → newest)

---

## Phase 6 — Validation & Error Handling

- [ ] Add consistent enum validation for timeframe/indicatorType/state/sort
- [ ] Return 400 for invalid inputs
- [ ] Return 200 + empty list when no data found
- [ ] Add `@ControllerAdvice` (or equivalent) for uniform 400 responses
- [ ] Add tests verifying 400 behavior for invalid params

---

## Phase 7 — Performance & Index Check

- [ ] Confirm index exists for `signal_state` suitable for "latest per asset" query
- [ ] Confirm index exists for `market_breadth_snapshot` latest + range queries
- [ ] Ensure no N+1 queries (verify with logs if needed)

---

## Phase 8 — Integration Tests (Testcontainers)

- [ ] Seed:
  - [ ] active assets
  - [ ] signal_state rows for multiple days
  - [ ] market_breadth_snapshot rows for multiple days

- [ ] Integration test: `/api/v1/signals`
- [ ] Integration test: `/api/v1/signals` with state filter
- [ ] Integration test: `/api/v1/signals` with sort LAST_FLIP_DESC
- [ ] Integration test: `/api/v1/assets/by-state`
- [ ] Integration test: `/api/v1/market-pulse`
- [ ] Integration test: `/api/v1/market-pulse/history` order correctness
- [ ] Integration test: invalid params return 400

---

## Phase 9 — Manual Validation

- [ ] Run app + DB
- [ ] Verify with curl:
  - [ ] `/api/v1/signals?timeframe=D1&indicatorType=SUPERTREND`
  - [ ] `/api/v1/signals?timeframe=D1&indicatorType=SUPERTREND&state=BULLISH&sort=LAST_FLIP_DESC`
  - [ ] `/api/v1/assets/by-state?timeframe=D1&indicatorType=SUPERTREND&state=BEARISH`
  - [ ] `/api/v1/market-pulse?timeframe=D1&indicatorType=SUPERTREND`
  - [ ] `/api/v1/market-pulse/history?timeframe=D1&indicatorType=SUPERTREND`
- [ ] Confirm outputs match DB and finalized-day rules