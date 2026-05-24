# Roadmap: Project Colombo — v2.0 Multi-Timeframe Scan

**Generated:** 2026-05-24
**Phases:** 1
**Requirements:** 13 v2.0 requirements, 100% mapped ✓

---

## Phase Overview

| # | Phase | Goal | Requirements | Plans |
|---|-------|------|--------------|-------|
| 4 | Multi-Timeframe Scan | Evolve the scan API so conditions can target different timeframes in a single AND query | SCAN-01–13 | 3 |

> Phase numbering continues from v1.0 (phases 1–3 complete). Phase 4 = first phase of v2.0.

---

## Phase 4: Multi-Timeframe Scan

**Goal:** Extend the scan API (`POST /api/v1/scan`) so that each `ScanCondition` can specify its own timeframe. A single request can mix D1 and W1 conditions with AND logic, enabling queries like "W1 SuperTrend BULLISH AND D1 SuperTrend BULLISH AND D1 RSI ABOVE_60" without client-side intersection.

**Requirements:** SCAN-01 through SCAN-13

**Plans:** 3 (sequential)

### Wave 1

- [ ] **04-01-PLAN.md** — DTO evolution: add `timeframe` to `ScanCondition`; add `timeframe` to `MatchedIndicator`, `SupertrendMatch`, and `RsiMatch`; make `ScanRequest.timeframe` optional; update `ScanResponse` to remove or nullable the top-level `timeframe`

### Wave 2 *(blocked on Wave 1 — service must compile against updated DTOs)*

- [ ] **04-02-PLAN.md** — Service and validator update: `ScanValidator` resolves per-condition timeframe with fallback to request-level; `ScanService.execute()` evaluates each condition against its own timeframe's latest close time; TradingView URL uses highest timeframe present

### Wave 3 *(blocked on Wave 2 — tests must run against updated service)*

- [ ] **04-03-PLAN.md** — Test suite: update `ScanValidatorTest`, `ScanServiceTest`, `ScanIntegrationTest`; add multi-timeframe integration test (W1 BULLISH AND D1 BULLISH); verify backward compatibility (existing single-timeframe requests pass)

**Success Criteria:**

1. `POST /api/v1/scan` with conditions carrying mixed `timeframe` values (e.g., one W1 and one D1) returns assets satisfying ALL conditions across both timeframes
2. Each `MatchedIndicator` in the response carries a `timeframe` field showing which timeframe it came from
3. Existing single-timeframe scan requests (top-level `timeframe`, conditions without per-condition `timeframe`) work unchanged — no 400 errors, same results
4. A request with no top-level `timeframe` and at least one condition missing `timeframe` returns HTTP 400 with a clear error message
5. All existing test suites pass; new multi-timeframe integration test passes

---

## API Contract Delta

### `ScanCondition` — Before vs After

**Before:**
```json
{
  "indicatorType": "SUPERTREND",
  "state": "BULLISH",
  "maxDaysSinceFlip": 5
}
```

**After (per-condition timeframe):**
```json
{
  "timeframe": "W1",
  "indicatorType": "SUPERTREND",
  "state": "BULLISH",
  "maxDaysSinceFlip": 5
}
```

### `ScanRequest` — Before vs After

**Before:**
```json
{
  "timeframe": "D1",
  "operator": "AND",
  "conditions": [...]
}
```

**After (timeframe optional — can be omitted when all conditions carry their own):**
```json
{
  "operator": "AND",
  "conditions": [
    {"timeframe": "W1", "indicatorType": "SUPERTREND", "state": "BULLISH"},
    {"timeframe": "D1", "indicatorType": "SUPERTREND", "state": "BULLISH"},
    {"timeframe": "D1", "indicatorType": "RSI", "state": "ABOVE_60"}
  ]
}
```

### `MatchedIndicator` subtypes — Before vs After

**Before (`SupertrendMatch`):**
```json
{
  "indicatorType": "SUPERTREND",
  "state": "BULLISH",
  "event": "NONE",
  "daysSinceFlip": 3,
  "closeTime": "2026-05-18T00:00:00Z"
}
```

**After:**
```json
{
  "indicatorType": "SUPERTREND",
  "timeframe": "W1",
  "state": "BULLISH",
  "event": "NONE",
  "daysSinceFlip": 3,
  "closeTime": "2026-05-18T00:00:00Z"
}
```

---

## Design Notes

### Backward Compatibility

The key invariant: **any request valid in v1 must remain valid in v2.**

- If `ScanRequest.timeframe` is present and a `ScanCondition` has no `timeframe` → condition inherits the request-level value
- If `ScanRequest.timeframe` is present and a `ScanCondition` ALSO has `timeframe` → the condition's own value wins
- If `ScanRequest.timeframe` is absent and all conditions carry `timeframe` → valid, no fallback needed
- If `ScanRequest.timeframe` is absent and any condition has no `timeframe` → HTTP 400

### `ScanService` Execution Change

Currently `getLatestFinalizedCloseTime(Timeframe)` is called once for the whole request. After v2, it must be called per unique timeframe in the conditions. A simple approach: build a `Map<Timeframe, OffsetDateTime>` of resolved close times before iterating conditions.

### TradingView URL Timeframe

Priority order: `W1 > D1`. The chart URL uses the highest-granularity timeframe present in the matched conditions list. This is consistent with the Bullmania strategy intent — when both D1 and W1 match, the trader will likely want the weekly chart context.

---
*Roadmap created: 2026-05-24*
