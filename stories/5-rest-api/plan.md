```markdown
# Story 005 — Implementation Plan
REST API Layer (Read-only Query Endpoints)

This plan translates requirements into deterministic implementation steps.

Follow in order.

---

## Phase 1 — API Contract & DTOs

1. Define API base path:

   - `/api/v1`

2. Create DTOs (no entity exposure):

   - `SignalStateDto`
   - `MarketPulseDto`
   - `MarketPulseHistoryDto` (or reuse `MarketPulseDto` in a list)

3. Define enum DTO inputs (reuse domain enums where safe):

   - `Timeframe` (e.g., D1)
   - `IndicatorType` (e.g., SUPERTREND)
   - `SignalState` (BULLISH / BEARISH)
   - `SignalSort` (LAST_FLIP_ASC / LAST_FLIP_DESC / ASSET_ASC)

4. Add explicit mappers (manual mapping or MapStruct):

   - Entity → DTO only
   - No DTO → Entity required in this story

Do not proceed until DTOs compile and are used by controllers.

---

## Phase 2 — Repository Query Methods (Bulk, No Loops)

Add / confirm repository methods that support the endpoints without per-asset queries.

### 2.1 Active Assets
- `AssetRepository.findByActiveTrue()`
- or `AssetRepository.findActiveAssetIdsAndSymbols()` (projection)

### 2.2 Latest finalized signal state per asset
Implement one of:

A) SQL query (preferred for correctness/efficiency):
- Fetch latest `signal_state` per asset for given `(timeframe, indicator_type)` with `close_time < utcMidnightToday`

B) Two-step query (acceptable only if still bulk):
- Fetch max close_time per asset then fetch rows for those pairs

Also add query to find last flip per asset:
- last row where `event != NONE` (per asset) up to the same finalized boundary

All queries must be bulk and deterministic.

### 2.3 Market pulse snapshot
- Latest snapshot by `(timeframe, indicator_type)`:
  `findTopByTimeframeAndIndicatorTypeOrderBySnapshotCloseTimeDesc(...)`

### 2.4 Market pulse history
- Range query by `(timeframe, indicator_type, from, to)` ordered by `snapshotCloseTime ASC`

Do not proceed until repository methods return correct results from seeded data.

---

## Phase 3 — Time Model Helpers (Finalized Boundary + Days Since Flip)

1. Implement a small utility:

   - `UtcClock` / `TimeProvider` (injectable)
   - `utcMidnightToday()` helper

2. Implement `timeSinceFlipDays` logic:

   - `daysBetween(lastFlipTime, nowUtc)` as whole days
   - If no flip exists yet, use earliest known signal_state close_time or return null
     (choose one and be consistent; default to null)

3. Unit test time computations with fixed clock.

---

## Phase 4 — Service Layer (Query Services)

Create services that hide repository complexity:

### 4.1 SignalQueryService
Methods:
- `listSignals(timeframe, indicatorType, optionalStateFilter, sort)`
Returns list of `SignalStateDto` with:
- asset symbol
- trend_state
- lastFlipTime
- timeSinceFlipDays

Rules:
- only ACTIVE assets
- only FINALIZED data
- deterministic sorting
- no per-asset DB loops

### 4.2 MarketPulseQueryService
Methods:
- `getLatestPulse(timeframe, indicatorType)`
- `getPulseHistory(timeframe, indicatorType, from?, to?)`

Do not proceed until services are returning DTOs correctly.

---

## Phase 5 — Controllers (Endpoints)

Implement controllers under `/api/v1`.

### 5.1 GET /api/v1/signals
Query params:
- timeframe (required)
- indicatorType (required)
- state (optional)
- sort (optional)

Return:
- 200 + JSON array

### 5.2 GET /api/v1/assets/by-state
Query params:
- timeframe (required)
- indicatorType (required)
- state (required)

Return:
- 200 + JSON array (asset symbols)

### 5.3 GET /api/v1/market-pulse
Query params:
- timeframe (required)
- indicatorType (required)

Return:
- 200 + MarketPulseDto

### 5.4 GET /api/v1/market-pulse/history
Query params:
- timeframe (required)
- indicatorType (required)
- from (optional ISO-8601)
- to (optional ISO-8601)

Return:
- 200 + ordered list (oldest → newest)

---

## Phase 6 — Validation & Error Handling

1. Validate required params:
- missing timeframe/indicatorType/state → 400

2. Validate enum values:
- invalid timeframe/indicatorType/state/sort → 400

3. If no results:
- return 200 with empty list (never 500)

Implement using:
- Spring validation annotations + custom converters
- `@ControllerAdvice` for consistent 400 responses

---

## Phase 7 — Performance / Index Check

1. Confirm indexes exist (or add if missing):

- `signal_state` index:
  `(timeframe, indicator_type, close_time, asset_id)`
  and/or `(asset_id, timeframe, indicator_type, close_time DESC)`

- `market_breadth_snapshot` index:
  `(timeframe, indicator_type, snapshot_close_time DESC)`

2. Ensure no N+1 query patterns exist.

---

## Phase 8 — Testing

### 8.1 Unit Tests
- Sorting behavior
- timeSinceFlipDays computation with fixed clock
- DTO mapping

### 8.2 Integration Tests (Testcontainers)
- Seed assets + signal_state + market_breadth_snapshot
- Call each endpoint using MockMvc / WebTestClient
- Assert:
  - 200 responses
  - correct filtering
  - correct sorting
  - correct finalized boundary behavior

---

## Phase 9 — Manual Verification

1. Run app + DB
2. Hit endpoints with curl:
- `/api/v1/signals?timeframe=D1&indicatorType=SUPERTREND`
- `/api/v1/signals?timeframe=D1&indicatorType=SUPERTREND&state=BULLISH&sort=LAST_FLIP_DESC`
- `/api/v1/market-pulse?timeframe=D1&indicatorType=SUPERTREND`
- `/api/v1/market-pulse/history?timeframe=D1&indicatorType=SUPERTREND`

3. Verify results match DB rows and expectations.
```
