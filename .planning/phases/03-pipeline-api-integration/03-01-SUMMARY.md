---
phase: 03-pipeline-api-integration
plan: "01"
subsystem: ingestion-pipeline
tags: [pipeline, w1, rollup, integration-test, pipe-01, pipe-02, pipe-03]
dependency_graph:
  requires:
    - 02-02 (W1IndicatorService as single W1 orchestrator entry point)
    - 01-01 (CandleRollupService for D1->W1 rollup)
  provides:
    - MarketPipelineService.runDaily() with 6-phase D1+W1 execution
    - Integration test proving full D1+W1 pipeline run
  affects:
    - MarketPipelineService (modified)
    - MarketPipelineIntegrationTest (modified)
    - MarketPipelineServiceTest (fixed)
tech_stack:
  added: []
  patterns:
    - Additive phase extension inside existing try block
    - Constructor injection of CandleRollupService and W1IndicatorService
key_files:
  modified:
    - backend/java/src/main/java/walshe/projectcolumbo/ingestion/MarketPipelineService.java
    - backend/java/src/test/java/walshe/projectcolumbo/ingestion/MarketPipelineIntegrationTest.java
    - backend/java/src/test/java/walshe/projectcolumbo/ingestion/MarketPipelineServiceTest.java
decisions:
  - Hardcode D1->W1 and DayOfWeek.MONDAY in pipeline (service is generic; pipeline is specific)
  - W1 phases run inside existing try block so W1 failures use existing catch/finalizeRun handling
  - 16 weeks (112 days) of D1 fixture ensures >= 15 W1 candles for RSI warm-up period
metrics:
  duration_minutes: 25
  completed_date: 2026-05-21
  tasks_completed: 3
  files_modified: 3
---

# Phase 03 Plan 01: Wire W1 Pipeline into runDaily() Summary

Single `runDaily()` call now produces D1 candles/indicators/signals/breadth AND W1 candles/indicators/signals/breadth via CandleRollupService (D1->W1 rollup) and W1IndicatorService (SuperTrend+RSI+signal+breadth), with D1 phases byte-for-byte unchanged and the IngestionRun record reflecting the full six-phase run.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Append W1 phases to MarketPipelineService.runDaily() | 472efab | MarketPipelineService.java |
| 2 | Add full D1+W1 pipeline integration test | f264f22 | MarketPipelineIntegrationTest.java, MarketPipelineServiceTest.java |
| 3 | Full-suite phase gate | (no code changes) | — |

## Verification Results

- `./mvnw compile -q` exits 0 after Task 1
- `MarketPipelineIntegrationTest` passes 4/4 after Task 2 (including new shouldProduceW1OutputsAfterFullPipelineRun)
- Full `./mvnw test` BUILD SUCCESS: 154 tests, 0 failures, 0 errors, 0 skipped

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] MarketPipelineServiceTest constructor mismatch**
- **Found during:** Task 2 (test-compile)
- **Issue:** `MarketPipelineServiceTest` constructed `MarketPipelineService` with 8 args; Task 1 added 2 more required constructor params (`CandleRollupService`, `W1IndicatorService`), causing compilation failure
- **Fix:** Added `@Mock CandleRollupService candleRollupService` and `@Mock W1IndicatorService w1IndicatorService` fields, passed them as the 9th and 10th constructor arguments in `setUp()`
- **Files modified:** `MarketPipelineServiceTest.java`
- **Commit:** f264f22

## Requirements Satisfied

- **PIPE-01**: A single `runDaily()` call produces W1 candles, indicators, signals, and market breadth
- **PIPE-02**: The four D1 phases run unchanged before any W1 work; all existing pipeline tests still pass
- **PIPE-03**: The `IngestionRun` record is finalized with SUCCESS status reflecting the full six-phase run including W1

## Known Stubs

None — all assertions in the integration test target real database state via Testcontainers.

## Threat Flags

None — no new HTTP endpoints, auth paths, file access patterns, or schema changes introduced. T-03-02 (W1 exception handling) and T-03-03 (W1 audit logging) mitigations are present: W1 phases run inside the existing try block, and per-phase start/complete log lines are emitted for both W1_ROLLUP and W1_PROCESSING.

## Self-Check: PASSED

- [x] MarketPipelineService.java exists with W1 phases
- [x] MarketPipelineIntegrationTest.java has shouldProduceW1OutputsAfterFullPipelineRun
- [x] MarketPipelineServiceTest.java compiles with updated constructor
- [x] Commit 472efab exists (Task 1)
- [x] Commit f264f22 exists (Task 2)
- [x] Full test suite BUILD SUCCESS (154 tests, 0 failures)
