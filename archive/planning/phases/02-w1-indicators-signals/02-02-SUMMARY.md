---
phase: 02-w1-indicators-signals
plan: "02"
subsystem: market-pulse
tags: [java, spring, supertrend, rsi, signal-state, market-pulse, integration-test, testcontainers, tdd]

requires:
  - phase: 02-w1-indicators-signals
    plan: "01"
    provides: [MarketPulseService.computeForTimeframe(Timeframe)]
  - phase: 01-w1-candle-derivation
    provides: [W1 candles in candle table, Timeframe.W1 enum value]
provides:
  - W1IndicatorService.processAllActiveAssets() — single-call Phase 3 entry point for full W1 pipeline
  - W1IndicatorPipelineIntegrationTest — 6 integration tests covering INDC-01/02/03, SGNL-01/02, and orchestrator ordering
  - Verified W1 indicator/signal/breadth pipeline end-to-end against Testcontainers Postgres
affects: [Phase 3 pipeline wiring — MarketPipelineService.runDaily() W1 integration]

tech-stack:
  added: []
  patterns:
    - "Thin orchestrator pattern: W1IndicatorService sequences 4 services in strict serial dependency order"
    - "log-before/log-after-with-duration per phase step (mirrors MarketPipelineService lines 84-102)"
    - "Testcontainers integration test with 15-candle W1 fixture for RSI-14 minimum (period+1)"
    - "FK-safe BeforeEach teardown order: breadth -> signals -> indicators -> candles -> assets"

key-files:
  created:
    - backend/java/src/main/java/walshe/projectcolumbo/marketpulse/W1IndicatorService.java
    - backend/java/src/test/java/walshe/projectcolumbo/persistence/service/W1IndicatorPipelineIntegrationTest.java
  modified: []

key-decisions:
  - "W1IndicatorService uses detectDaily() (not a new detectW1()) because detectDaily() already iterates Timeframe.values() which includes W1 after Phase 1 (RESEARCH Pitfall 1)"
  - "fullRecalc=false enforced in W1IndicatorService — incremental computation satisfies INDC-03 idempotency requirement"
  - "Test uses isNotEmpty() not hasSize(N) for most assertions — avoids brittle ATR/RSI warmup-dependent exact counts; exception is INDC-03 which asserts count equality across two runs"
  - "orchestrator_processAllActiveAssets_producesAllOutputs calls w1IndicatorService.processAllActiveAssets() once with no direct calls to underlying services — proves strict ordering and completeness"

patterns-established:
  - "Thin orchestrator: W1IndicatorService is a @Service that injects and sequences existing timeframe-parameterized services — no calculator logic, no repository access"
  - "W1 fixture: 15 Sunday-close W1 candles starting 2024-01-07 satisfies both RSI-14 (15 candles) and SuperTrend-10 (10 candles) minimums"

requirements-completed: [INDC-01, INDC-02, INDC-03, SGNL-01, SGNL-02]

duration: ~25 minutes
completed: 2026-05-21
---

# Phase 02 Plan 02: W1 Indicator/Signal/Breadth Orchestrator and Integration Tests Summary

**`W1IndicatorService` orchestrator (SuperTrend W1 -> RSI W1 -> detectDaily -> breadth W1) proven end-to-end by 6 Testcontainers integration tests covering all five Phase 2 requirements**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-05-21T16:30:00Z
- **Completed:** 2026-05-21T17:05:00Z
- **Tasks:** 3 (Tasks 1+2 produced code; Task 3 was full-suite phase gate)
- **Files modified:** 2 created, 0 modified

## Accomplishments

- Created `W1IndicatorPipelineIntegrationTest` with 6 `@Test` methods: five per-requirement (INDC-01/02/03, SGNL-01/02) plus one orchestrator end-to-end test
- Created `W1IndicatorService` orchestrator that sequences all four pipeline steps in strict serial order with phase logging
- Full Maven test suite (153 tests) passes with BUILD SUCCESS — no D1 regression

## Task Commits

Each task was committed atomically:

1. **Task 1: Create W1IndicatorPipelineIntegrationTest** - `2238afb` (test)
2. **Task 2: Implement W1IndicatorService orchestrator** - `385862e` (feat)
3. **Task 3: Full-suite phase gate** — verification only, no code changes (all 153 tests pass)

## Files Created/Modified

- `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/W1IndicatorService.java` — @Service orchestrator, @Transactional processAllActiveAssets(), 74 lines
- `backend/java/src/test/java/walshe/projectcolumbo/persistence/service/W1IndicatorPipelineIntegrationTest.java` — 6 integration tests with W1 candle fixture helper, 179 lines

## Decisions Made

- Used `detectDaily()` rather than a new `detectW1()` — `detectDaily()` iterates `Timeframe.values()` which now includes `W1` since Phase 1. Creating a duplicate would risk `UNIQUE constraint` violations (RESEARCH Pitfall 1).
- Used `isNotEmpty()` assertions (not `hasSize(N)`) in most tests to avoid brittleness from ATR warmup behaviour differences. INDC-03 correctly uses count equality (`isEqualTo`) across two runs.
- `fullRecalc=false` enforced and grep-gated in acceptance criteria — ensures idempotency (INDC-03) and mirrors production incremental mode.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None — all tests passed on first run with no assertion failures.

## User Setup Required

None - no external service configuration required.

## Threat Surface Scan

No new HTTP endpoints, auth paths, file access patterns, or schema changes introduced. `W1IndicatorService` is a data-pipeline-internal orchestrator. The `orchestrator_processAllActiveAssets_producesAllOutputs` integration test directly validates the T-02-03 mitigation (strict step ordering). T-02-04 (audit logging) is satisfied by log-before/log-after-with-duration in each pipeline step. T-02-05 (idempotency) is validated by `indicators_W1_areIdempotent`.

## Next Phase Readiness

- Phase 3 can wire `w1IndicatorService.processAllActiveAssets()` directly into `MarketPipelineService.runDaily()` — single clean call site, directly validated
- All five Phase 2 requirements (INDC-01/02/03, SGNL-01/02) are covered by automated integration tests
- Full suite (153 tests) is green with zero regressions

## Self-Check: PASSED

- [x] `W1IndicatorService.java` exists at `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/W1IndicatorService.java`
- [x] `W1IndicatorPipelineIntegrationTest.java` exists at `backend/java/src/test/java/walshe/projectcolumbo/persistence/service/W1IndicatorPipelineIntegrationTest.java`
- [x] Commit `2238afb` exists in git log (Task 1 - test)
- [x] Commit `385862e` exists in git log (Task 2 - feat)
- [x] 6 @Test methods present in integration test file
- [x] `./mvnw test -Dtest=W1IndicatorPipelineIntegrationTest` passes 6/6
- [x] `./mvnw test` passes 153/153 with BUILD SUCCESS

---
*Phase: 02-w1-indicators-signals*
*Completed: 2026-05-21*
