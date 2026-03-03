# Story 010 — Composable Market Scan API v2 (State + Event Logic)

## Plan

Enhance the existing `/api/v1/scan` to support both **events** (transitions that happened on the latest finalized candle) and **states** (ongoing conditions), plus optional recency (`maxDaysSinceFlip`). Keep the endpoint composable, extensible, and backward-compatible.

---

## Phase 1 — API Contract & DTOs

### 1.1 Endpoint
- `POST /api/v1/scan` — JSON request/response.

### 1.2 Request DTOs (contract)

```java
class ScanRequest {
  Timeframe timeframe;             // required
  ScanOperator operator;           // AND | OR
  List<ScanCondition> conditions;  // required, non-empty
  Integer limit;                   // optional pagination cap
}

class ScanCondition {
  IndicatorType indicatorType;     // required
  Event event;                     // optional
  TrendState state;                // optional
  Integer maxDaysSinceFlip;        // optional, requires state
}
```

### 1.3 Response DTOs

```java
class ScanResponse {
  Timeframe timeframe;
  ScanOperator operator;
  List<ScanCondition> conditions; 
  List<ScanResult> results;
  OffsetDateTime generatedAt;
}

class ScanResult {
  String assetSymbol;
  List<MatchedIndicator> matchedIndicators;
}

class MatchedIndicator {
  IndicatorType indicatorType;
  // either event or state description present (or both)
  Event event;              // optional
  TrendState state;         // optional
  OffsetDateTime closeTime; // for event or last state change
  Integer flippedDaysAgo;   // optional convenience (for state)
}
```

### 1.4 Backward compatibility

Event-only payloads work unchanged.

New fields (state, maxDaysSinceFlip) are optional.

---

## Phase 2 — Validation Rules & Layer

### 2.1 Request validation (ScanValidator)

- `conditions` must be non-empty.

For each `ScanCondition`:
- `indicatorType` must exist in `indicator_type` ENUM.
- At least one of `event` or `state` must be present.
- If `maxDaysSinceFlip` is present, `state` must also be present.
- `event` must be valid for the `indicatorType` (map of valid events per indicator).
- `state` must be valid for the `indicatorType`.
- `timeframe` must be a supported enum value.
- `operator` ∈ {AND, OR}.

Invalid inputs → 400 Bad Request with structured error body.

### 2.2 Unit coverage

Tests for every invalid combination and several valid permutations.

---

## Phase 3 — Data & Query Design

### 3.1 Data sources

- **Primary:** `signal_state` table (contains `indicator_type`, `event`, `trend_state`, `close_time`, `asset_id`, `timeframe`).
- **Secondary:** `asset` table (resolve `asset_symbol`).

No schema changes required for the baseline story. (If `flippedDaysAgo` convenience is preferred, compute from `close_time` at runtime.)

### 3.2 Indexes (ensure present)

- `(indicator_type, event, timeframe, close_time)` — supports event lookups.
- `(indicator_type, trend_state, timeframe, close_time)` — supports state queries and recency filters.
- `(asset_id, timeframe, close_time)` — used by other flows; verify.

### 3.3 Query semantics

**Event match (strict):** latest finalized candle `close_time` with `event = :event` and `indicator_type = :indicatorType` for the requested timeframe.

**SQL sketch:**
```sql
SELECT asset_id, indicator_type, event, close_time
FROM signal_state
WHERE indicator_type = :indicatorType
  AND event = :event
  AND timeframe = :timeframe
  AND close_time = (
    SELECT MAX(close_time) FROM signal_state WHERE timeframe = :timeframe
  );
```

**State match (ongoing):** latest `trend_state = :state` rows for assets in timeframe. Optionally restrict recency:

If `maxDaysSinceFlip` provided, require `close_time >= (current_utc_day - interval 'maxDaysSinceFlip days')`.

**SQL sketch:**
```sql
SELECT asset_id, indicator_type, trend_state, close_time
FROM signal_state
WHERE indicator_type = :indicatorType
  AND trend_state = :state
  AND timeframe = :timeframe
  AND close_time = (
    SELECT MAX(close_time) FROM signal_state ss2 
    WHERE ss2.asset_id = signal_state.asset_id 
      AND ss2.indicator_type = :indicatorType 
      AND ss2.timeframe = :timeframe
  )
  AND (close_time >= :threshold) -- only if maxDaysSinceFlip provided
;
```

### 3.4 Aggregation semantics

For each condition produce a set of `asset_ids` (and captured matched rows).

Combine sets by operator:
- **AND** → set intersection.
- **OR** → set union.

For final assets, return per-asset matched indicator details (include `close_time`/`flippedDaysAgo`).

---

## Phase 4 — Service Layer

### 4.1 ScanService responsibilities

- Accept validated `ScanRequest`.
- For each condition:
  - Execute event or state query (as above).
  - Build map: `asset_id -> list<MatchedIndicator>`.
- Combine `asset_id` sets by operator.
- Resolve `asset_symbol` for each `asset_id`.
- Build `ScanResponse` with `matchedIndicators` per asset (include `flippedDaysAgo` calculation if state-based).
- Respect `limit` (apply after combining results, deterministic ordering by `flippedDaysAgo` or `close_time` as reasonable).
- Log execution details (duration, matched count, request fingerprint).

### 4.2 Concurrency & performance

- Read-only workload — safe to run concurrently.
- Use single query per condition where possible rather than per-asset queries.
- Optionally cache the latest finalized `close_time` per timeframe for the duration of request to avoid repeated subqueries.

---

## Phase 5 — Controller & Error Handling

### 5.1 ScanController

`@PostMapping("/api/v1/scan")` consumes `ScanRequest`.

Steps:
1. Validate via `ScanValidator`.
2. Call `ScanService.execute(request)`.
3. Return 200 OK with `ScanResponse`.

Error handling:
- `BadRequestException` → 400.
- Unexpected errors → 500 with minimal internal details; log full stacktrace.

### 5.2 Observability

Add structured logs:
- `request hash`, `operator`, `timeframe`, `conditions summary`, `elapsed_ms`, `results_count`.

Add tracing span if tracing is in use.

---

## Phase 6 — Testing

### 6.1 Unit tests

- **ScanValidatorTest:** invalid indicator/event combos, missing required fields, `maxDaysSinceFlip` without state, etc.
- **ScanServiceTest:** mocked repository layer to simulate:
  - event-only matches,
  - state-only matches (with and without recency),
  - combined event+state per condition,
  - AND/OR set-combinations.
- Tests for deterministic ordering and limit behavior.

### 6.2 Integration tests (Testcontainers)

Seed `signal_state` with scenarios:
- **Asset A:** SuperTrend flipped bullish 3 days ago (`state=BULLISH`), RSI crossed above 60 today (event).
- **Asset B:** SuperTrend flipped bullish 10 days ago, RSI crossed today.
- **Asset C:** SuperTrend bearish, RSI crossed above 60 today.

Run scan requests:
- `SuperTrend(state=BULLISH, maxDaysSinceFlip=5) AND RSI(event=CROSSED_ABOVE_60)` → returns Asset A only.
- `SuperTrend(state=BULLISH) AND RSI(event=CROSSED_ABOVE_60)` → returns Assets A and B.
- `RSI(event=CROSSED_ABOVE_60)` alone → returns A, B, C’s matching entries.

Validate `flippedDaysAgo` correctness and `close_time` propagation.

Measure execution time for N assets (validate < 500ms for 1000 assets target).

---

## Phase 7 — Performance & Extensibility

### 7.1 Performance
- Ensure proper DB indexes exist; add if necessary.
- Avoid N+1 DB patterns; fetch per-condition sets in single queries.
- Consider materialized view later if scans become heavy and frequently repeated.

### 7.2 Extensibility
- Design `ScanCondition` and validator so adding new indicators/events requires only:
  - adding enum values,
  - adding validation mapping (indicator → allowed events/states).
- Future: support nested logical groups (expressive trees), additional operators (NOT), and time-window relative conditions.

---

## Phase 8 — Backward Compatibility & Rollout

- The API remains backward-compatible: clients using event-only payloads unchanged.
- Feature-flag the new validation logic if desired to ease rollout.
- Add OpenAPI examples documenting new state and `maxDaysSinceFlip` usage.

---

## Phase 9 — Acceptance Criteria / Done

- `POST /api/v1/scan` accepts conditions with event, state, or both.
- Correctly returns assets where:
  - event-based conditions fired on latest finalized candle,
  - state-based conditions reflect current `trend_state` (and respect `maxDaysSinceFlip`),
  - AND / OR logic behaves as specified.
- Validation rejects invalid indicator/event/state combinations.
- Unit and integration test suites pass.
- Performance target met for expected dataset sizes.
- Documentation updated (OpenAPI + examples).

---

## Notes / Implementation hints

- Treat "latest finalized candle" as a single resolved timestamp per timeframe (compute once per request).
- `flippedDaysAgo = floor((current_utc_day_start - close_time) / 1 day)` — return integer days for convenient sorting.
- For state checks, prefer querying the latest `signal_state` row per asset+indicator (use `DISTINCT ON (asset_id)` or window function) rather than scanning all history.
