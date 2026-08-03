---
phase: 03-pipeline-api-integration
reviewed: 2026-05-22T00:00:00Z
depth: standard
files_reviewed: 4
files_reviewed_list:
  - backend/java/src/main/java/walshe/projectcolumbo/ingestion/MarketPipelineService.java
  - backend/java/src/test/java/walshe/projectcolumbo/ingestion/MarketPipelineIntegrationTest.java
  - backend/java/src/test/java/walshe/projectcolumbo/ingestion/MarketPipelineServiceTest.java
  - backend/java/src/test/java/walshe/projectcolumbo/api/v1/W1ApiIntegrationTest.java
findings:
  critical: 2
  warning: 3
  info: 3
  total: 8
status: issues_found
---

# Phase 03: Code Review Report

**Reviewed:** 2026-05-22T00:00:00Z
**Depth:** standard
**Files Reviewed:** 4
**Status:** issues_found

## Summary

Four files were reviewed: `MarketPipelineService` (the new six-phase pipeline orchestrator), `MarketPipelineIntegrationTest`, `MarketPipelineServiceTest`, and `W1ApiIntegrationTest`. Supporting files `IngestionOrchestrator`, `W1IndicatorService`, `MarketPulseService`, and `SignalStateService` were read to trace call chains.

Two critical defects were found. First, when `finalizeRun` itself throws an exception, the `IngestionRun` record is saved to the database with status still `RUNNING`, permanently blocking future pipeline executions via the concurrency guard. Second, the `MarketPulseService.upsertSnapshot` revision path silently does nothing — if a snapshot already exists and differs from the recomputed value, the stale data is retained and only a warning is logged, making the "upsert" effectively an insert-once.

Three warnings cover: `signalStateService.detectDaily()` being called twice per successful pipeline run (Phases 3 and 6 both invoke it, with Phase 3's W1 pass running before W1 indicators exist), the unit test not verifying phases 5 and 6 in the ordering assertion, and `unknownCount` being computed but not stored in the snapshot entity.

---

## Critical Issues

### CR-01: `IngestionRun` Permanently Stuck in RUNNING Status If `finalizeRun` Throws

**File:** `backend/java/src/main/java/walshe/projectcolumbo/ingestion/MarketPipelineService.java:129-134`

**Issue:** When a pipeline exception is caught at line 129, `finalizeRun(run, null, e)` is called, which delegates to `IngestionOrchestrator.finalizeRun`. That method calls `run.setFinishedAt(OffsetDateTime.now())` and `run.setStatus(IngestionRunStatus.FAILED)`. If `IngestionOrchestrator.finalizeRun` itself throws for any reason (e.g. a null `startedAt` causes `Duration.between` to throw, or the repository call inside it fails), the exception propagates out of the outer `catch` block. Control then passes directly to the `finally` block at line 132, which calls `ingestionRunRepository.save(run)`. At this point `run` still has `status = RUNNING` because `finalizeRun` never completed its status mutation. The record is persisted with status `RUNNING` and no `finishedAt`.

The concurrency guard at lines 69-74 queries for any `RUNNING` record for the same provider+timeframe and throws `IngestionAlreadyRunningException` if one is found. Since this record will never be cleaned up automatically, all future pipeline executions are permanently blocked until a developer manually updates the database row.

**Fix:** Wrap the `finalizeRun` call in a nested try/catch inside the outer `catch` block, and guarantee a fallback status write:

```java
} catch (Exception e) {
    logger.error("Market pipeline failed during execution: {}", e.getMessage(), e);
    try {
        finalizeRun(run, null, e);
    } catch (Exception finalizationError) {
        logger.error("Failed to finalize run after pipeline error; forcing FAILED status", finalizationError);
        run.setStatus(IngestionRunStatus.FAILED);
        run.setFinishedAt(OffsetDateTime.now());
        // Inline truncation since MarketPipelineService has no truncate helper
        String msg = e.getMessage();
        run.setErrorSample(msg != null && msg.length() > 1000 ? msg.substring(0, 1000) : msg);
    }
} finally {
    ingestionRunRepository.save(run);
}
```

---

### CR-02: `MarketPulseService.upsertSnapshot` Silently Discards Snapshot Revisions

**File:** `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/MarketPulseService.java:120-134`

**Issue:** The `upsertSnapshot` method detects when an existing snapshot differs from a newly computed one (the `!isSame` branch at line 122), logs a REVISION warning, and then does absolutely nothing — the comment reads "For now, let's just log and skip or update if needed" and no update is performed. The `MarketBreadthSnapshot` entity has no setters (confirmed: the constructor-only design at lines 56-66 of the entity class), so updating in place is not possible without adding setters or using a delete-and-reinsert pattern.

This means the function is effectively insert-once: after the first successful snapshot is written, any pipeline re-run that produces different values (e.g. after an indicator recomputation, or an asset becoming active/inactive) will silently preserve the stale snapshot. The API will serve incorrect breadth data indefinitely, and the log warning creates a false impression that the revision was handled.

**Fix:** Use delete-and-reinsert since the entity has no setters:

```java
if (existingOpt.isPresent()) {
    MarketBreadthSnapshot existing = existingOpt.get();
    if (isSame(existing, snapshot)) {
        log.debug("MarketPulse snapshot unchanged for {}", snapshot.getSnapshotCloseTime());
    } else {
        log.warn("REVISION: MarketPulse snapshot changed for {}. Replacing.", snapshot.getSnapshotCloseTime());
        snapshotRepository.delete(existing);
        snapshotRepository.flush(); // ensure delete is flushed before re-insert within the same transaction
        snapshotRepository.save(snapshot);
    }
} else {
    snapshotRepository.save(snapshot);
    log.info("Created new MarketPulse snapshot for {}", snapshot.getSnapshotCloseTime());
}
```

---

## Warnings

### WR-01: `signalStateService.detectDaily()` Invoked Twice Per Pipeline Run, First Call Runs Before W1 Data Exists

**File:** `backend/java/src/main/java/walshe/projectcolumbo/ingestion/MarketPipelineService.java:104` and `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/W1IndicatorService.java:66`

**Issue:** `signalStateService.detectDaily()` iterates `Timeframe.values()` (both D1 and W1). It is called at Phase 3 of `MarketPipelineService` (line 104), before the W1 rollup (Phase 5) or W1 indicator computation (Phase 6). At that point, no W1 candles or W1 indicators exist yet, so the W1 pass in Phase 3 produces only `UNKNOWN` signal state records for W1 (the "Case A: new asset" path in `SignalStateService.processAssetForIndicator`). `detectDaily()` is then called again inside `W1IndicatorService.processAllActiveAssets()` at Phase 6 (line 66), after W1 indicators are properly computed.

The Phase 6 call is the correct, useful one. The Phase 3 W1 pass is spurious: it writes junk `UNKNOWN` W1 signal rows that are immediately overwritten by Phase 6, and it causes the API to briefly serve `UNKNOWN` W1 signals in the window between Phases 3 and 6. The `W1IndicatorService` Javadoc comment (line 47) acknowledges that `detectDaily()` covers W1 "automatically" but does not call out the double-invocation problem.

**Fix:** Scope Phase 3 of `MarketPipelineService` to D1 only. Add a `detectForTimeframe(Timeframe)` method to `SignalStateService` and call it from Phase 3 with `Timeframe.D1`:

```java
// Phase 3: Signal Detection (D1 only; W1 is handled by W1IndicatorService in Phase 6)
signalStateService.detectForTimeframe(Timeframe.D1);
```

And update `W1IndicatorService` to call `detectForTimeframe(Timeframe.W1)` rather than `detectDaily()`.

---

### WR-02: Unit Test `runDaily_shouldExecutePhasesInCorrectOrder` Does Not Verify W1 Phases 5 and 6

**File:** `backend/java/src/test/java/walshe/projectcolumbo/ingestion/MarketPipelineServiceTest.java:86-97`

**Issue:** The `InOrder` verification at line 91 only includes the five D1 services: `candleIngestionService`, `superTrendService`, `rsiComputationService`, `signalStateService`, `marketPulseService`. The two new phases — `candleRollupService.rollupForAllActiveAssets` (Phase 5) and `w1IndicatorService.processAllActiveAssets` (Phase 6) — are never verified. Both are declared as mocks (lines 46 and 48) but receive no assertion. If Phases 5 and 6 were deleted from `runDaily`, or if their order relative to Phase 4 were swapped, this test would still pass. The primary value of this test (verifying correct execution order of the new phases) is missing.

**Fix:** Extend the `InOrder` block to all six phases:

```java
InOrder inOrder = inOrder(candleIngestionService, superTrendService, rsiComputationService,
        signalStateService, marketPulseService, candleRollupService, w1IndicatorService);
inOrder.verify(candleIngestionService).ingestDaily();
inOrder.verify(superTrendService).processAllActiveAssets(eq(Timeframe.D1), anyInt(), any(), eq(false));
inOrder.verify(rsiComputationService).computeForActiveAssets(eq(Timeframe.D1), anyInt(), eq(false));
inOrder.verify(signalStateService).detectDaily();
inOrder.verify(marketPulseService).computeDaily();
inOrder.verify(candleRollupService).rollupForAllActiveAssets(eq(Timeframe.D1), eq(Timeframe.W1), any());
inOrder.verify(w1IndicatorService).processAllActiveAssets();
```

---

### WR-03: `unknownCount` Computed but Not Stored — `missingCount` Semantics Are Inconsistent

**File:** `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/MarketPulseService.java:91-107`

**Issue:** `unknownCount` is computed at line 91 (assets whose latest signal state is `UNKNOWN`) and used only to subtract from `missingCount` at line 92. The `MarketBreadthSnapshot` constructor at line 99 receives `missingCount` but not `unknownCount`. This means the snapshot stores: bullish, bearish, and missing — where "missing" is defined as assets that have no signal row at all (neither unknown nor trend-bearing). Assets with `UNKNOWN` trend state vanish from the snapshot without being attributed to any category.

This is a data model inconsistency: the computation correctly distinguishes four states (bullish, bearish, unknown, missing) but the stored entity collapses it to three (bullish, bearish, missing-excluding-unknown). A reader of the snapshot cannot reconstruct the true `totalAssets` from the three stored counts when there are unknown-trend assets.

**Fix:** Either (a) expand `MarketBreadthSnapshot` to include `unknownCount` as a stored field, or (b) define "missing" as "unknown + truly-missing" and simplify the computation:

```java
// Option b — simpler, explicit:
int missingCount = (int) (totalActiveAssets - bullishCount - bearishCount);
// Remove the unknownCount variable; it is no longer needed.
```

This makes the contract clear: `missingCount` = all non-bullish, non-bearish assets (including UNKNOWN ones).

---

## Info

### IN-01: Unused Imports in `MarketPipelineService`

**File:** `backend/java/src/main/java/walshe/projectcolumbo/ingestion/MarketPipelineService.java:12,18`

**Issue:** Two imports are present but unused:
- Line 12: `import walshe.projectcolumbo.persistence.model.IndicatorType;` — `IndicatorType` is referenced nowhere in the class body.
- Line 18: `import java.time.Duration;` — `Duration` is not used; timing is done with `System.currentTimeMillis()` arithmetic. `Duration` is only used inside `IngestionOrchestrator`.

**Fix:** Remove both unused imports.

---

### IN-02: `RunMode` Parameter of `runDaily` Is Accepted but Never Read

**File:** `backend/java/src/main/java/walshe/projectcolumbo/ingestion/MarketPipelineService.java:60,64`

**Issue:** The `RunMode mode` parameter is declared at line 60 and commented at line 64 as "currently always INCREMENTAL in logic". The parameter value is never read in the method body. All call sites (scheduler and tests) pass `RunMode.INCREMENTAL`, but the method behaves identically regardless of what is passed. This creates a misleading API contract — callers might reasonably expect that passing `RunMode.FULL_RECALC` would trigger a full recomputation, but it has no effect.

**Fix:** Either remove the parameter and update callers, or add a documented TODO and a guard:
```java
if (mode == RunMode.FULL_RECALC) {
    throw new UnsupportedOperationException("FULL_RECALC mode not yet implemented");
}
```

---

### IN-03: `MarketPipelineServiceTest.runDaily_shouldStopOnFailure` Does Not Verify W1 Services Are Also Skipped

**File:** `backend/java/src/test/java/walshe/projectcolumbo/ingestion/MarketPipelineServiceTest.java:108-109`

**Issue:** The failure-path test verifies `verifyNoInteractions(superTrendService, rsiComputationService, signalStateService, marketPulseService)` but omits `candleRollupService` and `w1IndicatorService`. If either Phase 5 or Phase 6 were accidentally placed before the try block or had their exception handling inverted, this test would not catch the regression.

**Fix:** Add the W1 services to the no-interaction assertion:
```java
verifyNoInteractions(superTrendService, rsiComputationService, signalStateService,
        marketPulseService, candleRollupService, w1IndicatorService);
```

---

_Reviewed: 2026-05-22T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
