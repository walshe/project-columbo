---
phase: 03-pipeline-api-integration
fixed_at: 2026-05-22T00:00:00Z
review_path: .planning/phases/03-pipeline-api-integration/03-REVIEW.md
iteration: 1
findings_in_scope: 5
fixed: 5
skipped: 0
status: all_fixed
---

# Phase 03: Code Review Fix Report

**Fixed at:** 2026-05-22T00:00:00Z
**Source review:** .planning/phases/03-pipeline-api-integration/03-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 5 (CR-01, CR-02, WR-01, WR-02, WR-03)
- Fixed: 5
- Skipped: 0

## Fixed Issues

### CR-01: `IngestionRun` Permanently Stuck in RUNNING Status If `finalizeRun` Throws

**Files modified:** `backend/java/src/main/java/walshe/projectcolumbo/ingestion/MarketPipelineService.java`
**Commit:** 7099b5b
**Applied fix:** Wrapped the `finalizeRun(run, null, e)` call inside the outer `catch` block in its own `try/catch`. If `finalizeRun` throws, the fallback block now forces `run.setStatus(FAILED)`, `run.setFinishedAt(now())`, and `run.setErrorSample(...)` directly before the `finally` block saves the record. The `finally` block's `ingestionRunRepository.save(run)` can no longer persist a `RUNNING` record.

---

### CR-02: `MarketPulseService.upsertSnapshot` Silently Discards Snapshot Revisions

**Files modified:** `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/MarketPulseService.java`
**Commit:** a84e32a
**Applied fix:** Replaced the no-op revision branch with a delete-and-reinsert pattern. When the snapshot differs, the existing entity is deleted, the delete is flushed within the same transaction, and the new snapshot is saved. The `isSame` log was also demoted from `info` to `debug` since it fires on every unchanged pipeline run. The `MarketBreadthSnapshot` entity has no setters, so delete-and-reinsert is the correct approach.

---

### WR-01: `signalStateService.detectDaily()` Invoked Twice Per Pipeline Run

**Files modified:**
- `backend/java/src/main/java/walshe/projectcolumbo/persistence/service/SignalStateService.java`
- `backend/java/src/main/java/walshe/projectcolumbo/ingestion/MarketPipelineService.java`
- `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/W1IndicatorService.java`
- `backend/java/src/test/java/walshe/projectcolumbo/ingestion/MarketPipelineServiceTest.java`

**Commit:** f4cfdf5
**Applied fix:** Added `detectForTimeframe(Timeframe)` to `SignalStateService` — it runs the same per-asset processing loop as `detectDaily()` but for a single timeframe only. Phase 3 in `MarketPipelineService` now calls `detectForTimeframe(Timeframe.D1)`, preventing premature W1 signal rows from being written before W1 candles exist. `W1IndicatorService.processAllActiveAssets()` now calls `detectForTimeframe(Timeframe.W1)` instead of `detectDaily()` to avoid redundant D1 re-detection in Phase 6. The ordering test was updated to verify `detectForTimeframe(D1)` as part of this change since the call site changed.

---

### WR-02: Unit Test Does Not Verify W1 Phases 5 and 6

**Files modified:** `backend/java/src/test/java/walshe/projectcolumbo/ingestion/MarketPipelineServiceTest.java`
**Commit:** 0918d5b
**Applied fix:** Extended the `InOrder` constructor call to include `candleRollupService` and `w1IndicatorService`, then added `inOrder.verify(candleRollupService).rollupForAllActiveAssets(eq(Timeframe.D1), eq(Timeframe.W1), any())` and `inOrder.verify(w1IndicatorService).processAllActiveAssets()` after the existing Phase 4 verification. Deleting or reordering either W1 phase will now fail this test.

---

### WR-03: `unknownCount` Computed but Not Stored; `missingCount` Semantics Inconsistent

**Files modified:** `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/MarketPulseService.java`
**Commit:** f6efdf6 — requires human verification (logic change)
**Applied fix:** Applied Option b from the review: redefined `missingCount` as `totalActiveAssets - bullishCount - bearishCount`, which collapses UNKNOWN-trend and truly-missing assets into a single "not bullish/bearish" bucket. Removed the `unknownCount` variable entirely. The stored snapshot now satisfies the invariant `bullishCount + bearishCount + missingCount == totalActiveAssets` for all inputs. If the product intent is to distinguish UNKNOWN from truly-missing in the snapshot, Option a (adding an `unknownCount` column) would be required instead and would need a DB migration.

---

_Fixed: 2026-05-22T00:00:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
