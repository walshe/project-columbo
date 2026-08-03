---
phase: 01-w1-candle-derivation
plan: 01
subsystem: database
tags: [flyway, postgresql, java, enum, timeframe, jpa]

# Dependency graph
requires: []
provides:
  - "PostgreSQL timeframe enum extended with 'W1' value via Flyway migration V13"
  - "Java Timeframe enum constant W1(\"1W\") for type-safe references"
  - "TimeframeTest.java covering all W1 and D1 behaviors"
affects:
  - "02-w1-candle-derivation (CandleRollupService needs Timeframe.W1)"
  - "Any code iterating Timeframe.values() now sees W1"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Flyway plain SQL migration for PostgreSQL enum extension (mirrors V8 precedent)"
    - "TDD: failing test commit (RED) before implementation commit (GREEN)"

key-files:
  created:
    - "backend/java/src/main/resources/db/migration/V13__add_w1_timeframe.sql"
    - "backend/java/src/test/java/walshe/projectcolumbo/persistence/model/TimeframeTest.java"
  modified:
    - "backend/java/src/main/java/walshe/projectcolumbo/persistence/model/Timeframe.java"

key-decisions:
  - "Plain SQL migration file for ALTER TYPE enum extension (mirrors V8), not Java-based migration class — V8 precedent proves Flyway 11 PostgreSQL module handles it non-transactionally"
  - "W1 string value is '1W' following the existing D1('1D') convention (assumption A5)"
  - "No spring.flyway.mixed=true added — would weaken safety for all other migrations unnecessarily"

patterns-established:
  - "Enum extension pattern: ALTER TYPE ... ADD VALUE IF NOT EXISTS 'W1' as plain SQL, no transaction directives"
  - "Timeframe enum: new constants follow D1('1D') convention with two-char name and value as inverted"

requirements-completed: [CNDL-05]

# Metrics
duration: 13min
completed: 2026-05-20
---

# Phase 1 Plan 01: W1 Timeframe Foundation Summary

**PostgreSQL timeframe enum extended to include 'W1' via Flyway V13 migration, and Java Timeframe enum constant W1("1W") added with full TDD coverage — the foundational change all downstream W1 work in this phase depends on.**

## Performance

- **Duration:** ~13 min
- **Started:** 2026-05-20T19:07:05Z
- **Completed:** 2026-05-20T19:20:05Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- Created `V13__add_w1_timeframe.sql` — additive, idempotent Flyway migration using `ALTER TYPE timeframe ADD VALUE IF NOT EXISTS 'W1'`; mirrors proven V8 approach
- Added `W1("1W")` constant to `Timeframe.java` — no changes to constructor, `getValue()`, or `fromValue()` (generic iteration handles new constants automatically)
- Created `TimeframeTest.java` — plain JUnit 5 unit test (no Spring context) covering all 7 behaviors: W1 constant exists, getValue() returns "1W", fromValue by value and by name for W1, D1 regression checks, values() length check

## Task Commits

Each task was committed atomically:

1. **Task 1: Add Flyway migration V13** - `2637e58` (feat)
2. **Task 2 RED: Failing TimeframeTest** - `02ac846` (test)
3. **Task 2 GREEN: Add W1("1W") to Timeframe** - `792ffbb` (feat)

## TDD Gate Compliance

- RED gate (`test(01-01)` commit): `02ac846` — tests fail to compile because `Timeframe.W1` does not exist
- GREEN gate (`feat(01-01)` commit after RED): `792ffbb` — all 7 test assertions pass
- REFACTOR gate: not needed — change was a single constant addition with no cleanup required

## Files Created/Modified

- `backend/java/src/main/resources/db/migration/V13__add_w1_timeframe.sql` — Flyway migration adding 'W1' to PostgreSQL timeframe enum
- `backend/java/src/main/java/walshe/projectcolumbo/persistence/model/Timeframe.java` — Added `W1("1W")` constant after `D1("1D")`
- `backend/java/src/test/java/walshe/projectcolumbo/persistence/model/TimeframeTest.java` — JUnit 5 unit tests for all W1 and D1 behaviors

## Decisions Made

- Plain SQL migration (not Java-based) for the enum extension, matching V8 precedent. The plan explicitly resolved this open question: Flyway 11's PostgreSQL module handles `ALTER TYPE ... ADD VALUE` non-transactionally without per-file config.
- `IF NOT EXISTS` added for idempotency — safe to re-run against a database where W1 already exists.
- No modifications to `fromValue()` or `getValue()` in Timeframe.java — the existing implementation iterates `values()` and matches on both string value and enum name case-insensitively, so it handles W1 automatically.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None. The Maven wrapper (`mvnw`) was used instead of a system-installed `mvn` (not available in the execution environment) — this is the standard project convention and not a deviation.

## User Setup Required

None — no external service configuration required. Flyway will apply V13 automatically on next application startup.

## Next Phase Readiness

- `Timeframe.W1` is now available for all downstream code in this phase
- `CandleRollupService` (Plan 02) can reference `Timeframe.W1` without compilation errors
- D1 pipeline behavior is unchanged — no regressions
- The migration will apply atomically at startup; the `IF NOT EXISTS` guard makes it safe even if applied against a DB that already has W1

---
*Phase: 01-w1-candle-derivation*
*Completed: 2026-05-20*
