---
phase: 01-w1-candle-derivation
plan: "03"
subsystem: backend/java
tags: [integration-test, testcontainers, candle-rollup, w1, postgres, flyway]
dependency_graph:
  requires:
    - 01-01 (Timeframe.W1 enum + V13 Flyway migration)
    - 01-02 (CandleRollupService implementation)
  provides:
    - Testcontainers integration tests proving DB-enum validity, idempotency, and incremental rollup
  affects:
    - backend/java/src/test/java/walshe/projectcolumbo/rollup/
tech_stack:
  added: []
  patterns:
    - "@SpringBootTest + @Import(TestcontainersConfiguration.class) Testcontainers integration test"
    - "Postgres 16 Testcontainers with all 13 Flyway migrations applied on startup"
    - "AssertJ assertions for OHLCV correctness and row-identity invariants"
key_files:
  created:
    - backend/java/src/test/java/walshe/projectcolumbo/rollup/CandleRollupIntegrationTest.java
  modified: []
decisions:
  - "Seeded test fixtures at Monday 2025-01-06 UTC (week A) and Monday 2025-01-13 UTC (week B) — past dates guarantee the completeness guard treats them as finalized"
  - "Used the exact @SpringBootTest + @Import(TestcontainersConfiguration.class) pattern from SuperTrendRepositoryTest"
  - "DeleteAll order: candleRepository first, then assetRepository (respects FK constraint)"
  - "rollup_isIncremental asserts week A row identity by id and closeTime — the incremental guard (lastStoredCloseTime) correctly skips already-stored W1 weeks on second run"
metrics:
  duration: "~90 minutes (including two full Testcontainers Postgres startup cycles)"
  completed: "2026-05-21"
  tasks_completed: 2
  tasks_total: 2
  files_created: 1
  files_modified: 0
---

# Phase 01 Plan 03: Testcontainers Integration Tests for W1 Rollup Summary

**One-liner:** Testcontainers integration tests against real Postgres 16 confirm W1 is a valid DB enum value (V13 migration), the rollup is idempotent across repeated runs (unique constraint + upsert), and incremental when new D1 data arrives.

## What Was Built

`CandleRollupIntegrationTest.java` — three JUnit 5 integration tests backed by a real Testcontainers PostgreSQL 16 database with all 13 Flyway migrations applied:

| Test | Requirement | What it proves |
|------|------------|----------------|
| `w1_timeframeExistsInDb` | CNDL-05 | W1 is a valid PostgreSQL `timeframe` enum value; V13 migration applies cleanly; OHLCV aggregation is correct end-to-end |
| `rollup_isIdempotent` | CNDL-03 | Calling `rollupForAllActiveAssets` twice for the same D1 source data produces exactly one W1 row — the `unique_asset_timeframe_close` constraint + upsert holds |
| `rollup_isIncremental` | CNDL-03 | Seeding week B D1 candles and re-running produces exactly two W1 rows; week A's row id and closeTime are unchanged |

### Full Suite Verification

`./mvnw test` (146 tests across all test classes): `Tests run: 146, Failures: 0, Errors: 0, Skipped: 0`. BUILD SUCCESS. No `ALTER TYPE ... ADD VALUE cannot run inside a transaction block` error appeared in any integration test run. The `Timeframe.W1` constant introduced no regressions in pre-existing tests.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Write Testcontainers integration tests for W1 rollup | 5ea39be | `CandleRollupIntegrationTest.java` (created) |
| 2 | Run full test suite as phase verification gate | (no commit — verification only) | — |

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as written.

### Prerequisite Merge

The plan's `depends_on` listed `01-01` and `01-02`. This worktree was created from `main` before the plan 01-01 and 01-02 commits were merged to `feature/weekly-candles`. A `git merge feature/weekly-candles` was required before implementation to obtain `Timeframe.W1`, `V13__add_w1_timeframe.sql`, and `CandleRollupService.java`. This is normal parallel-executor startup behavior, not a deviation.

## Known Stubs

None — the test file wires directly to the live Spring context and real Testcontainers Postgres; no hardcoded empty values or placeholder data flows to production code.

## Threat Surface Scan

This plan adds test code only. No new HTTP endpoints, auth paths, file access patterns, or schema changes were introduced. The threat model items T-01-10, T-01-11, T-01-12, and T-01-SC from the plan are all satisfied:

- **T-01-10 (idempotency):** `rollup_isIdempotent` proves the DB unique constraint prevents duplication.
- **T-01-11 (incremental re-runs):** `rollup_isIncremental` confirms no unbounded re-aggregation on re-run.
- **T-01-12 (test fixtures):** Fixtures are synthetic OHLCV integers — no secrets or PII.
- **T-01-SC (no new packages):** Confirmed — Testcontainers, JUnit 5, and Postgres driver were already in `pom.xml`.

## Self-Check: PASSED

- [x] `CandleRollupIntegrationTest.java` exists at correct path
- [x] Class annotated `@SpringBootTest` and `@Import(TestcontainersConfiguration.class)`
- [x] Three test methods: `w1_timeframeExistsInDb`, `rollup_isIdempotent`, `rollup_isIncremental`
- [x] `./mvnw test -Dtest=CandleRollupIntegrationTest`: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`
- [x] `./mvnw test` (full suite): `Tests run: 146, Failures: 0, Errors: 0, Skipped: 0`
- [x] No `ALTER TYPE ... ADD VALUE cannot run inside a transaction block` error
- [x] SUMMARY.md written at `.planning/phases/01-w1-candle-derivation/01-03-SUMMARY.md`
