---
phase: 03-pipeline-api-integration
plan: "02"
subsystem: api
tags: [integration-tests, w1, api, timeframe, market-pulse, signals, scan]
dependency_graph:
  requires: []
  provides: [W1ApiIntegrationTest, API-01-verified, API-02-verified, API-03-verified]
  affects: [backend/java/src/test/java/walshe/projectcolumbo/api/v1/W1ApiIntegrationTest.java]
tech_stack:
  added: []
  patterns: [SpringBootTest integration test, MockMvc, Testcontainers, fixture seeding]
key_files:
  created:
    - backend/java/src/test/java/walshe/projectcolumbo/api/v1/W1ApiIntegrationTest.java
  modified: []
decisions:
  - "Assert timeframe as '1W' not 'W1' in JSON — Timeframe enum uses @JsonValue serializing to its display value (e.g. W1 -> '1W')"
metrics:
  duration_minutes: 15
  completed_date: "2026-05-21"
  tasks_completed: 3
  files_created: 1
  files_modified: 0
---

# Phase 03 Plan 02: W1 API Integration Tests Summary

W1 integration tests proving all three query/scan REST endpoints correctly serve W1 data when `timeframe=W1` is requested, with cross-timeframe isolation verified via seeded D1 fixtures.

## What Was Built

Created `W1ApiIntegrationTest` covering all three existing REST endpoints with W1 fixtures:

- **`marketPulseEndpoint_returnsW1Breadth`** (API-01): Seeds both a D1 breadth snapshot (bullishCount=7) and a W1 breadth snapshot (bullishCount=12), then requests `GET /api/v1/market-pulse?timeframe=W1&indicatorType=SUPERTREND`. Asserts the W1 bullishCount=12 is returned — not the D1 row.

- **`signalsEndpoint_returnsW1SignalStates`** (API-02): Seeds a D1 BEARISH signal and a W1 BULLISH signal for the same asset (BTCUSDT). Requests `GET /api/v1/signals?timeframe=W1&indicatorType=SUPERTREND`. Asserts exactly 1 result is returned with `trendState=BULLISH` — confirming W1 scoping and D1 exclusion.

- **`scanEndpoint_returnsW1Results`** (API-03): Seeds a W1 Candle (required for `ScanService.getLatestFinalizedCloseTime` to build the event-match window), a W1 BULLISH_REVERSAL signal, and a D1 BULLISH_REVERSAL signal for the same asset. Posts `ScanRequest(Timeframe.W1, AND, [SUPERTREND BULLISH_REVERSAL])`. Asserts `timeframe=1W`, `results` has exactly 1 entry with `assetSymbol=BTCUSDT`.

## Tasks

| Task | Description | Commit | Status |
|------|-------------|--------|--------|
| 1 | Create W1ApiIntegrationTest: market-pulse + signals tests | 19b047f | Done |
| 2 | Add scan endpoint test (scanEndpoint_returnsW1Results) | 19b047f | Done (same commit — file created atomically) |
| 3 | Full-suite phase gate: ./mvnw test BUILD SUCCESS | No commit (verify-only) | Done |

## Test Results

- `W1ApiIntegrationTest`: 3/3 methods pass
- Full suite: 156 tests, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS
- Pre-existing `ApiIntegrationTest` (8 tests) and `ScanIntegrationTest` (7 tests) all pass — no regression

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed incorrect timeframe JSON assertion in scan test**
- **Found during:** Task 2 verification (first test run)
- **Issue:** The plan's acceptance criteria called for `jsonPath("$.timeframe").value("W1")`, but the `Timeframe` enum uses `@JsonValue` on `getValue()` which returns `"1W"` for `Timeframe.W1`. The JSON response therefore contained `"timeframe": "1W"`, not `"W1"`.
- **Fix:** Changed assertion to `.andExpect(jsonPath("$.timeframe").value("1W"))`
- **Files modified:** `W1ApiIntegrationTest.java` (before commit)
- **Commit:** 19b047f (fix was applied inline before the commit)

**2. [Implementation deviation] Tasks 1 and 2 committed together**
- Tasks 1 and 2 modified the same file. The file was written atomically with all 3 tests in a single creation pass. Both tasks are covered by commit 19b047f. The scan test (Task 2) was authored alongside Tasks 1 tests rather than as a separate edit, since it required the same class scaffolding and setup.

## Known Stubs

None.

## Threat Surface Scan

No new HTTP endpoints added. No production code modified. This plan adds test coverage only.

Threat mitigations verified by tests:
- **T-03-05 (Information Disclosure — cross-timeframe leakage):** All three tests seed both D1 and W1 fixtures and assert only W1 data is returned. D1 rows do not appear in W1 responses.
- **T-03-04 (Tampering — invalid timeframe param):** Covered by pre-existing `ApiIntegrationTest.shouldReturn400ForInvalidParams` (passes in full suite).

## Self-Check

### Files Exist
- `backend/java/src/test/java/walshe/projectcolumbo/api/v1/W1ApiIntegrationTest.java` — FOUND

### Commits Exist
- `19b047f` — FOUND (test(03-02): add W1ApiIntegrationTest)

## Self-Check: PASSED
