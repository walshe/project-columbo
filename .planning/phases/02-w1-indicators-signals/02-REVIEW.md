---
phase: 02-w1-indicators-signals
reviewed: 2026-05-21T00:00:00Z
depth: standard
files_reviewed: 4
files_reviewed_list:
  - backend/java/src/main/java/walshe/projectcolumbo/marketpulse/MarketPulseService.java
  - backend/java/src/test/java/walshe/projectcolumbo/marketpulse/MarketPulseServiceTest.java
  - backend/java/src/main/java/walshe/projectcolumbo/marketpulse/W1IndicatorService.java
  - backend/java/src/test/java/walshe/projectcolumbo/persistence/service/W1IndicatorPipelineIntegrationTest.java
findings:
  critical: 2
  warning: 4
  info: 2
  total: 8
status: issues_found
---

# Phase 02: Code Review Report

**Reviewed:** 2026-05-21T00:00:00Z
**Depth:** standard
**Files Reviewed:** 4
**Status:** issues_found

## Summary

Four files were reviewed: the `MarketPulseService` aggregation service, its unit test, the `W1IndicatorService` orchestrator, and an integration test covering the full W1 pipeline. Cross-referencing was performed against `SignalStateRepository`, `MarketBreadthSnapshot`, `TrendState`, `IndicatorType`, and `SignalStateService`.

Two blockers were identified. The first is a silent data-corruption bug in the upsert path where a changed snapshot is detected but silently discarded rather than updated — the stale record is never replaced and the caller receives no error. The second is a calculation correctness bug: `UNKNOWN` states are counted against the `presentCount` denominator used for the bullish ratio, causing the ratio to be under-reported whenever assets carry an UNKNOWN RSI or SuperTrend state. Four warnings cover the transaction coupling between `W1IndicatorService.processAllActiveAssets()` and `SignalStateService.detectDaily()` (REQUIRES_NEW inner transactions eat failures silently), a `missingCount` that can go negative, an incomplete test stub left in production code, and an assumption that `IndicatorType.values()` order does not matter. Two info items cover dead code and a magic-number parameter.

---

## Critical Issues

### CR-01: Silent no-op on snapshot revision — stale data persisted indefinitely

**File:** `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/MarketPulseService.java:124-130`

**Issue:** `upsertSnapshot()` detects that an existing snapshot differs from the freshly computed one (line 125 logs `REVISION: MarketPulse snapshot changed`) but then does nothing — no field update, no delete-and-reinsert. The old, incorrect snapshot remains in the database permanently. Any re-run of the pipeline after a late-arriving candle or a corrected indicator will silently produce the wrong breadth figures in all downstream queries.

The comment in the dead branch makes the intent clear ("we might need them or just delete/insert") but the implementation never follows through. The `MarketBreadthSnapshot` entity deliberately has no setters, so the update path has zero effect even if setters were added later without revisiting this code.

**Fix:**
```java
if (existingOpt.isPresent()) {
    MarketBreadthSnapshot existing = existingOpt.get();
    if (isSame(existing, snapshot)) {
        log.info("MarketPulse snapshot already exists and is identical for {}", snapshot.getSnapshotCloseTime());
    } else {
        log.warn("REVISION: MarketPulse snapshot changed for {}. Replacing.", snapshot.getSnapshotCloseTime());
        snapshotRepository.delete(existing);
        snapshotRepository.flush();          // ensure DELETE committed before re-insert to avoid unique constraint violation
        snapshotRepository.save(snapshot);
        log.info("Replaced MarketPulse snapshot for {}", snapshot.getSnapshotCloseTime());
    }
} else {
    snapshotRepository.save(snapshot);
    log.info("Created new MarketPulse snapshot for {}", snapshot.getSnapshotCloseTime());
}
```

---

### CR-02: `UNKNOWN` states included in `presentCount` denominator, corrupting bullish ratio

**File:** `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/MarketPulseService.java:89-96`

**Issue:** `presentCount` is calculated as `bullishCount + bearishCount` (line 94), which correctly excludes UNKNOWN. However, `unknownCount` is separately counted from `statesAtTime` on line 91, and `statesAtTime` is the result of `findLatestFinalizedForActiveAssets()` — a query that returns all signal states, including RSI states whose `TrendState` can be `ABOVE_60`, `BELOW_40`, or `NEUTRAL` (values present in the `TrendState` enum but not covered by the three explicit filters on lines 89–91).

Any asset with an RSI `TrendState` of `ABOVE_60`, `BELOW_40`, or `NEUTRAL` is counted by none of the three filters, falls silently into a hole, and is then also not captured in `unknownCount`. Those assets contribute to neither numerator nor denominator, yet they are subtracted from `totalActiveAssets` through `missingCount`. This means the ratio can be computed over a smaller-than-intended population without any log or error indicating data was lost. Additionally, for the RSI indicator specifically the bullish/bearish framing itself is meaningless (RSI uses a different state vocabulary), so the snapshot for `IndicatorType.RSI` will always show zero bullish, zero bearish, and a ratio of `0.0`, which is misleading rather than absent.

**Fix:** Either restrict `computeForTimeframe` to only `IndicatorType.SUPERTREND` (which is the only indicator that uses BULLISH/BEARISH states) until RSI breadth semantics are defined, or add explicit handling for RSI states:

```java
// Option A: Guard at the loop level in computeForTimeframe
for (IndicatorType type : IndicatorType.values()) {
    if (type == IndicatorType.RSI) {
        // RSI uses a different state vocabulary (ABOVE_60/BELOW_40/NEUTRAL).
        // Breadth aggregation for RSI is not yet defined — skip to avoid silent zeros.
        log.debug("Skipping MarketPulse aggregation for RSI (state vocabulary mismatch)");
        continue;
    }
    computePulseForIndicator(timeframe, type);
}
```

---

## Warnings

### WR-01: `W1IndicatorService.processAllActiveAssets()` outer `@Transactional` swallows `SignalStateService` per-asset failure isolation

**File:** `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/W1IndicatorService.java:52-73`

**Issue:** `processAllActiveAssets()` is annotated `@Transactional`, which opens a single outer transaction for the entire pipeline pass. However, `SignalStateService.processAsset()` is annotated `@Transactional(propagation = Propagation.REQUIRES_NEW)`, which suspends the outer transaction and opens a new one per asset. Any failure inside `processAsset` that is caught by `detectDaily`'s per-asset `try/catch` (line 92 in `SignalStateService`) will roll back that inner transaction but leave the outer `W1IndicatorService` transaction open and apparently clean.

The concrete risk: if SuperTrend and RSI rows are written in the outer transaction, then `detectDaily` runs and a per-asset inner transaction partially fails but the exception is swallowed, the breadth snapshot in step 4 then sees the committed SuperTrend/RSI rows but zero signal states for the failed asset — leading to a signal gap that looks like a legitimate data gap rather than a processing error. No exception surfaces to the caller.

**Fix:** Remove `@Transactional` from `W1IndicatorService.processAllActiveAssets()`. Each sub-service manages its own transaction scope. The orchestrator does not need a wrapping transaction; cross-service atomicity is not a viable goal here and the annotation provides a false safety guarantee.

```java
// Remove @Transactional — each phase manages its own transactional scope
public void processAllActiveAssets() {
    ...
}
```

---

### WR-02: `missingCount` can be negative when `UNKNOWN`, `ABOVE_60`, `BELOW_40`, or `NEUTRAL` states are present

**File:** `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/MarketPulseService.java:92`

**Issue:** `missingCount = (int)(totalActiveAssets - bullishCount - bearishCount - unknownCount)`. If `statesAtTime` contains assets with `TrendState.ABOVE_60`, `TrendState.BELOW_40`, or `TrendState.NEUTRAL` (all valid enum values, all returned by RSI signal detection), those states are counted by none of the three filters and contribute zero to the subtracted terms. The subtracted sum will undercount by however many such states exist, making `missingCount` correct only by accident. In the worst case (all RSI assets have non-UNKNOWN states), `missingCount` will be driven negative, which violates the implied invariant that `bullishCount + bearishCount + unknownCount + missingCount == totalActiveAssets`.

**Fix:** Count all non-bullish, non-bearish states as "not bullish/bearish" and subtract the entire `statesAtTime` size from `totalActiveAssets`:

```java
int presentCount = bullishCount + bearishCount;
int missingCount = (int)(totalActiveAssets - statesAtTime.size());
// unknownCount can still be computed separately for diagnostics but should not participate in the above arithmetic
```

---

### WR-03: Incomplete upsert implementation — dead comment block treated as production code

**File:** `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/MarketPulseService.java:126-130`

**Issue:** The revision branch inside `upsertSnapshot()` contains four lines of comments describing what needs to be done ("we'd update fields here", "we might need them or just delete/insert", "Given the instructions, I should probably add setters") that read as developer notes from implementation time. This is not commented-out code in the traditional sense — it is an unfinished implementation decision left in the branch body. Combined with CR-01, the effect is that this code path actively conceals data quality problems from operators.

**Fix:** Delete the comment block entirely and replace it with the corrective action described in CR-01. If the implementation decision is genuinely deferred, add a `throw new UnsupportedOperationException("snapshot revision not implemented")` so failures are loud rather than silent.

---

### WR-04: Unit test only stubs `IndicatorType.SUPERTREND` — `IndicatorType.RSI` mock returns `null`, causing NPE potential

**File:** `backend/java/src/test/java/walshe/projectcolumbo/marketpulse/MarketPulseServiceTest.java:65-69`

**Issue:** In `shouldComputeDailyWithCorrectRatio()` and `computeForTimeframe_D1_delegatesComputeDaily_producesIdenticalSnapshot()`, the `signalStateRepository` mock is configured only for `IndicatorType.SUPERTREND`. `computeForTimeframe` iterates `IndicatorType.values()`, which includes `IndicatorType.RSI`. The unstubbed `RSI` call returns Mockito's default for `List` (`null` in older Mockito versions, or an empty list in newer ones). If the version in use returns `null`, then `latestStates.isEmpty()` at line 71 of `MarketPulseService` throws a `NullPointerException`.

Even if Mockito returns an empty list (the safer default), the test provides no assertion that RSI processing was skipped gracefully — it relies on the absence of exceptions rather than an explicit assertion about the RSI path. The RSI breadth scenario described in CR-02 is entirely untested.

**Fix:** Add explicit stubs for all `IndicatorType` values or scope the test to the SUPERTREND-only path with an explicit `verifyNoMoreInteractions(snapshotRepository)` guard. Also add a test case for the RSI indicator path to cover the state-vocabulary mismatch described in CR-02.

```java
// Stub RSI explicitly to avoid relying on Mockito default behavior
when(signalStateRepository.findLatestFinalizedForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.RSI), any()))
        .thenReturn(List.of());
when(snapshotRepository.findByTimeframeAndIndicatorTypeAndSnapshotCloseTime(eq(Timeframe.D1), eq(IndicatorType.RSI), any()))
        .thenReturn(Optional.empty());
```

---

## Info

### IN-01: `computeDaily()` is a redundant thin wrapper — dead entry point

**File:** `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/MarketPulseService.java:49-52`

**Issue:** `computeDaily()` simply calls `computeForTimeframe(Timeframe.D1)` and is only used by `MarketPipelineService.runDaily()`. The `W1IndicatorService` already calls `computeForTimeframe(Timeframe.W1)` directly. Keeping two entry points with slightly different names (`computeDaily` / `computeForTimeframe`) creates ambiguity about which one is canonical and invites callers to bypass the parameterized version. `MarketPipelineService` (line 101) should call `computeForTimeframe(Timeframe.D1)` directly.

**Fix:** Remove `computeDaily()` and update `MarketPipelineService.runDaily()` to call `computeForTimeframe(Timeframe.D1)` directly.

---

### IN-02: Magic numbers for SuperTrend and RSI parameters across three call sites

**File:** `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/W1IndicatorService.java:56,61`

**Issue:** `W1IndicatorService.processAllActiveAssets()` hardcodes `10`, `new BigDecimal("2.0")`, and `14` as SuperTrend and RSI parameters. The same values are also hardcoded in `MarketPipelineService.runDaily()` (lines 87, 89) and the integration test (lines 101, 121, 127, 138). Three independent call sites holding the same magic numbers means a parameter change requires three coordinated edits with no compiler enforcement.

**Fix:** Extract constants to a shared configuration class or `@ConfigurationProperties` bean:
```java
// In a shared constants class or application properties
private static final int SUPERTREND_PERIOD = 10;
private static final BigDecimal SUPERTREND_MULTIPLIER = new BigDecimal("2.0");
private static final int RSI_PERIOD = 14;
```

---

_Reviewed: 2026-05-21T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
