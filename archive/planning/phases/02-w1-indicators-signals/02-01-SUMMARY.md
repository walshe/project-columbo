---
phase: 02-w1-indicators-signals
plan: "01"
subsystem: market-pulse
tags: [java, spring, market-pulse, timeframe, refactor]
dependency_graph:
  requires: []
  provides: [MarketPulseService.computeForTimeframe(Timeframe)]
  affects: [MarketPipelineService.runDaily (backward-compat), Plan 02-02 W1 orchestrator]
tech_stack:
  added: []
  patterns: [log-before/log-after-with-duration (System.currentTimeMillis), TDD RED/GREEN]
key_files:
  modified:
    - backend/java/src/main/java/walshe/projectcolumbo/marketpulse/MarketPulseService.java
    - backend/java/src/test/java/walshe/projectcolumbo/marketpulse/MarketPulseServiceTest.java
decisions:
  - "@Transactional retained on computeDaily() (not removed) so proxy-based Spring TX still fires for legacy callers"
  - "log-before/log-after pattern mirrors MarketPipelineService lines 84-102 using System.currentTimeMillis()"
metrics:
  duration: ~8 minutes
  completed: 2026-05-21
---

# Phase 02 Plan 01: MarketPulseService Timeframe Parameterization Summary

**One-liner:** Extracted `computeForTimeframe(Timeframe)` from `computeDaily()` so market breadth aggregation can run on any timeframe, with `computeDaily()` delegating to it via `Timeframe.D1`.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 (RED) | Add failing test for computeForTimeframe | 2877a89 | MarketPulseServiceTest.java |
| 1 (GREEN) | Extract computeForTimeframe and delegate computeDaily | fafa1f5 | MarketPulseService.java |

## What Was Built

`MarketPulseService` now exposes a public `@Transactional computeForTimeframe(Timeframe)` method that:
- Logs start with timeframe name
- Loops `IndicatorType.values()` calling the already-parameterized private `computePulseForIndicator(timeframe, type)`
- Logs completion with elapsed milliseconds (mirroring `MarketPipelineService` phase-logging pattern)

`computeDaily()` now delegates: `computeForTimeframe(Timeframe.D1);` — single line, still `@Transactional`, zero behaviour change for existing callers.

The hardcoded `Timeframe timeframe = Timeframe.D1;` literal has been removed from `computeDaily()`.

## Acceptance Criteria Verification

- `computeForTimeframe(Timeframe` count = 1 (public method declaration)
- `computeForTimeframe(Timeframe.D1)` count = 1 (delegation call in `computeDaily()`)
- `Timeframe timeframe = Timeframe.D1` in non-comment lines = 0
- Both methods preceded by `@Transactional`
- `MarketPulseServiceTest` 3/3 tests pass (2 existing + 1 new GREEN test)
- `./mvnw compile -q` succeeds

## TDD Gate Compliance

- RED commit: `2877a89` — `computeForTimeframe_D1_delegatesComputeDaily_producesIdenticalSnapshot` test added, failed to compile (method did not exist)
- GREEN commit: `fafa1f5` — implementation added, all 3 tests pass

## Deviations from Plan

None — plan executed exactly as written.

## Threat Surface Scan

No new HTTP endpoints, auth paths, file access patterns, or schema changes introduced. Refactor is internal to the market-pulse data pipeline. No new threat surface beyond the threat model in the plan.

## Self-Check: PASSED

- [x] `MarketPulseService.java` exists and contains `computeForTimeframe`
- [x] RED commit `2877a89` exists in git log
- [x] GREEN commit `fafa1f5` exists in git log
- [x] `MarketPulseServiceTest` passes (3/3)
- [x] Project compiles
