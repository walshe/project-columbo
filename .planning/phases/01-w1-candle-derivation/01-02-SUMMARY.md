---
phase: 01-w1-candle-derivation
plan: 02
subsystem: database
tags: [java, spring, jpa, candle-rollup, timeframe, tdd]

# Dependency graph
requires:
  - phase: 01-w1-candle-derivation/01-01
    provides: W1 Timeframe enum constant and Flyway migration for DB enum
provides:
  - Generic CandleRollupService that aggregates source candles into target timeframe weeks
  - Week grouping via TemporalAdjusters.previousOrSame(weekStartDay)
  - Completeness guard (7 candles, finalized before UTC midnight today)
  - Incremental guard (skip already-stored weeks)
  - Idempotent upsert via findByAssetAndTimeframeAndCloseTime
affects:
  - 01-03 (integration test for CandleRollupService)
  - Any future rollup callers (H4->D1, H4->W1)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Generic timeframe rollup parameterized by sourceTimeframe/targetTimeframe/weekStartDay (no literals)
    - Week grouping with TreeMap<OffsetDateTime, List<Candle>> preserving chronological order
    - Per-asset try/catch with insert/update/skipped stats logging (mirrors SuperTrendService)
    - Idempotent upsert using ifPresentOrElse on findByAssetAndTimeframeAndCloseTime

key-files:
  created:
    - backend/java/src/main/java/walshe/projectcolumbo/rollup/CandleRollupService.java
  modified: []

key-decisions:
  - "closeTime taken directly from last source candle's data — never recomputed from calendar arithmetic (avoids DST/precision drift)"
  - "Week grouped by openTime (not closeTime) so all 7 days map to the same Monday key regardless of candle close offsets"
  - "Completeness guard requires exactly 7 source candles — any partial week (e.g., 5 of 7) is silently skipped without error"
  - "Incremental guard uses strictly-after comparison so exact-match weeks are also skipped, preventing duplicate writes"

patterns-established:
  - "Rollup grouping: candle.getOpenTime().withOffsetSameInstant(UTC).with(TemporalAdjusters.previousOrSame(weekStartDay)).withHour(0)..."
  - "findLatestCloseTime Object->OffsetDateTime: instanceof Instant ? instant.atOffset(UTC) : (OffsetDateTime) obj"

requirements-completed: [CNDL-01, CNDL-02, CNDL-04]

# Metrics
duration: 15min
completed: 2026-05-21
---

# Phase 1 Plan 02: CandleRollupService Summary

**Generic D1-to-W1 (and any source-to-target) candle rollup service with week grouping via TemporalAdjusters, 7-candle completeness guard, incremental skip, and idempotent upsert — all five unit tests pass (GREEN)**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-05-21T09:30:00Z
- **Completed:** 2026-05-21T09:45:00Z
- **Tasks:** 1 (Task 2 — GREEN implementation; Task 1 RED state was pre-completed)
- **Files modified:** 1

## Accomplishments
- Implemented `CandleRollupService` as a timeframe-generic `@Service` with no `Timeframe.D1` or `Timeframe.W1` literals
- Week grouping uses `TemporalAdjusters.previousOrSame(weekStartDay)` on candle `openTime`, collecting into a `TreeMap` for chronological ordering
- Completeness guard: exactly 7 source candles AND last candle `closeTime.isBefore(utcMidnightToday)` — partial and current weeks are silently skipped
- Incremental guard: weeks whose `closeTime` is not strictly after the last stored target `closeTime` are skipped, preventing duplicate writes
- Idempotent upsert: `findByAssetAndTimeframeAndCloseTime` + `ifPresentOrElse` — inserts new weeks, updates revised weeks, skips unchanged
- All five `CandleRollupServiceTest` tests pass: OHLCV correctness, open/close time sourcing from data, incomplete-week guard, timeframe-genericity, already-stored-week skipping

## Task Commits

1. **Task 1 (RED): Write failing unit tests** - `d915050` (test) — pre-completed before this execution
2. **Task 2 (GREEN): Implement CandleRollupService** - `584c3d4` (feat)

**Plan metadata:** (this summary commit — see final commit)

_TDD: RED commit was pre-existing; GREEN commit is 584c3d4_

## Files Created/Modified
- `backend/java/src/main/java/walshe/projectcolumbo/rollup/CandleRollupService.java` — Generic rollup service: week grouping, completeness guard, aggregation, incremental guard, idempotent upsert, per-asset stats logging

## Decisions Made
- `closeTime` for the rolled-up candle is taken directly from the last source candle's `closeTime` (not computed as `weekStart + 7 days - 1ms`) to avoid calendar arithmetic pitfalls (DST, precision)
- Week key uses `openTime` (not `closeTime`) for grouping so all 7 D1 candles for a Mon-Sun week bucket to Monday's key consistently
- Completeness requires exactly 7 candles — the plan specifies source timeframe is D1 and target is W1, so 7 is the canonical week size; a configurable minimum was not introduced (YAGNI for now)

## Deviations from Plan

None — plan executed exactly as written. The service mirrors the `SuperTrendService` pattern as specified.

## Issues Encountered

The worktree branch was behind `feature/weekly-candles` and did not have the prerequisite commits (W1 Timeframe enum, Flyway migration, test file). A `git merge feature/weekly-candles` fast-forward was performed before implementing Task 2. This is not a deviation — it was a worktree initialization gap, not an unplanned change.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `CandleRollupService` is ready for plan 01-03: integration test against a real Postgres DB via Testcontainers
- The service is discoverable as a Spring `@Service` bean — no wiring changes needed
- No known blockers

---
*Phase: 01-w1-candle-derivation*
*Completed: 2026-05-21*
