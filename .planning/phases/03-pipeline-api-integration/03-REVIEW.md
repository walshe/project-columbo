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
  critical: 3
  warning: 4
  info: 2
  total: 9
status: issues_found
---

# Phase 03: Code Review Report

**Reviewed:** 2026-05-22T00:00:00Z
**Depth:** standard
**Files Reviewed:** 4
**Status:** issues_found

## Summary

Four files reviewed: the new `MarketPipelineService` (production) and three test classes
(`MarketPipelineIntegrationTest`, `MarketPipelineServiceTest`, `W1ApiIntegrationTest`).

The production service has two serious correctness defects: the `IngestionRun` record can
be persisted with status `null` (never finalized) when an exception occurs *inside
`finalizeRun`*, and the phase-6 W1 pipeline always calls `signalStateService.detectDaily()`
which iterates **every** timeframe — meaning D1 signals are re-evaluated a second time in
every pipeline run, which contradicts the comment in `W1IndicatorService` and is wasteful at
best and produces double-write/revision-log noise at worst.

The third critical issue is in `MarketPipelineService.runDaily`: after an exception the
run status is set by `finalizeRun` but the updated run object is re-saved from the `finally`
block using the **local variable** `run`, which has already been re-assigned. However, when
`finalizeRun` itself throws, the finally-block saves the original RUNNING record to the
database as-is — no status, no error sample — so the run row is permanently stuck in
RUNNING status, blocking all future pipeline executions via the concurrency guard.

Additionally, the integration tests share a real Spring context across `@SpringBootTest`
classes without any isolation mechanism, and `W1ApiIntegrationTest` leaks `MockMvc`
initialisation outside of `@BeforeEach`, creating a fragile ordering dependency.

---

## Critical Issues

### CR-01: Run left permanently RUNNING if `finalizeRun` itself throws

**File:** `backend/java/src/main/java/walshe/projectcolumbo/ingestion/MarketPipelineService.java:129-134`

**Issue:** The `finally` block (line 133) saves `run` back to the database. This is intended
to persist the FAILED/SUCCESS status that `finalizeRun` wrote to the in-memory object. But
`finalizeRun` delegates entirely to `IngestionOrchestrator.finalizeRun`, which calls
`run.setFinishedAt(...)`, `run.setStatus(...)`, etc. If *that* method throws for any reason
(e.g., `Duration.between` on a null `startedAt`, or any of the setter calls), the exception
propagates out of the `catch` block; the `finally` block then saves `run` in whatever
incomplete state it was in — potentially still with `status = RUNNING` and no `finishedAt`.

Because the concurrency guard on line 69-74 throws `IngestionAlreadyRunningException` for
any RUNNING record, this permanently blocks the pipeline from ever running again without
manual DB intervention.

Even in the normal failure path, the flow is:
1. `catch(Exception e)` calls `finalizeRun(run, null, e)` — sets `run.status = FAILED`
2. `finally` calls `run = ingestionRunRepository.save(run)` — OK

But if `finalizeRun` throws at step 1, control jumps straight to `finally`, which saves the
stale `run` object (still RUNNING). The exception from `finalizeRun` is then swallowed
because `finally` itself does not rethrow (save succeeds), so the caller gets back a RUNNING
run object with no error recorded — silent data corruption.

**Fix:** Wrap `finalizeRun` in its own try/catch inside the `catch(Exception e)` block, and
guarantee a fallback status write:

```java
} catch (Exception e) {
    logger.error("Market pipeline failed: {}", e.getMessage(), e);
    try {
        finalizeRun(run, null, e);
    } catch (Exception finalizeEx) {
        logger.error("Failed to finalize run after pipeline error; forcing FAILED status", finalizeEx);
        run.setStatus(IngestionRunStatus.FAILED);
        run.setFinishedAt(OffsetDateTime.now());
        run.setErrorSample(truncate(e.getMessage(), 1000));
    }
} finally {
    ingestionRunRepository.save(run);
}
```

Note: `MarketPipelineService` currently has no `truncate` helper — copy the private method
from `IngestionOrchestrator`, or (better) expose it as package-private there.

---

### CR-02: Phase 6 (`W1IndicatorService.processAllActiveAssets`) re-runs D1 signal detection

**File:** `backend/java/src/main/java/walshe/projectcolumbo/ingestion/MarketPipelineService.java:122`
**Also:** `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/W1IndicatorService.java:66`

**Issue:** Inside `W1IndicatorService.processAllActiveAssets()`, the W1 signal step calls
`signalStateService.detectDaily()`. That method iterates `Timeframe.values()` (line 85 in
`SignalStateService`), so it processes **every** timeframe — including D1 — not just W1.

In a full pipeline run, Phase 3 already ran D1 signal detection (line 104 of
`MarketPipelineService`). Phase 6 then runs it again via `detectDaily()`. The second pass
is redundant for D1 and produces spurious REVISION-level log warnings when a signal row is
found to be identical (triggering the `skipped++` path) or, in edge cases where indicators
are re-computed between passes, may produce incorrect REVISION updates to finalized D1
signal states.

This is confirmed by the `W1IndicatorService` Javadoc (line 47): "detectDaily() — covers W1
automatically (iterates Timeframe.values())". The comment acknowledges the full-iteration
behaviour but treats it as acceptable. It is not: it causes double writes and noise.

**Fix:** Either (a) add a `detectForTimeframe(Timeframe timeframe)` method to
`SignalStateService` and call it with `Timeframe.W1` only from `W1IndicatorService`, or (b)
rename the Phase-3 call in `MarketPipelineService` to `detectForTimeframe(Timeframe.D1)` so
both passes are scoped. Option (a) is cleaner.

```java
// In W1IndicatorService (option a):
signalStateService.detectForTimeframe(Timeframe.W1);  // not detectDaily()
```

---

### CR-03: `IngestionController` returns 202 ACCEPTED with status "STARTED" but the pipeline has already completed (synchronous execution)

**File:** `backend/java/src/main/java/walshe/projectcolumbo/api/v1/IngestionController.java:30-33`

**Issue:** `IngestionController.triggerRun` calls `pipelineService.runDaily(...)` synchronously
and blocks until all six pipeline phases complete (potentially many minutes). It then returns
`HttpStatus.ACCEPTED` (202) with body `{"runId": X, "status": "STARTED"}`.

202 Accepted semantically means "the request has been accepted for processing but processing
has not completed". Here processing *has* already completed — the run is either SUCCESS or
FAILED by the time the response is sent. The status field literally says "STARTED" when the
run is finished. This is incorrect and misleading for any caller that uses the status to
decide whether to poll for completion.

Additionally, the caller has no way to tell whether the run succeeded or failed from the
response — both outcomes return 202 "STARTED". If the pipeline failed, the API returns a
success HTTP status with a misleading message.

**Fix:** Return 200 OK with the actual final status of the run:

```java
@PostMapping("/run")
ResponseEntity<IngestionResponse> triggerRun(@RequestBody(required = false) IngestionRequest request) {
    IngestionRequest safeRequest = request != null ? request : new IngestionRequest(null, null);
    IngestionRun run = pipelineService.runDaily(safeRequest.provider(), safeRequest.timeframe(), RunMode.INCREMENTAL);
    HttpStatus httpStatus = run.getStatus() == IngestionRunStatus.FAILED
            ? HttpStatus.INTERNAL_SERVER_ERROR
            : HttpStatus.OK;
    return ResponseEntity.status(httpStatus)
            .body(new IngestionResponse(run.getId(), run.getStatus().name()));
}
```

If true async execution is desired in future, 202 + polling is correct — but then
`runDaily` must be made non-blocking (e.g., with `@Async`).

---

## Warnings

### WR-01: `MarketPipelineService` imports `Duration` and `BigDecimal` that are unused at class level

**File:** `backend/java/src/main/java/walshe/projectcolumbo/ingestion/MarketPipelineService.java:18-20`

**Issue:** Lines 18 and 19 import `java.time.Duration` and `java.math.BigDecimal`. `BigDecimal`
is used inline on line 96 (`new BigDecimal("2.0")`), so that import is legitimately needed.
`java.time.Duration` is never used inside `MarketPipelineService` itself — `Duration` is only
referenced inside `IngestionOrchestrator.finalizeRun`. The import is dead code introduced
during refactoring.

**Fix:** Remove `import java.time.Duration;` from `MarketPipelineService.java`.

---

### WR-02: Unit test `runDaily_shouldExecutePhasesInCorrectOrder` does not verify W1 rollup or W1 indicator phases

**File:** `backend/java/src/test/java/walshe/projectcolumbo/ingestion/MarketPipelineServiceTest.java:86-97`

**Issue:** The `InOrder` verification (line 91) only covers the first five phases:
`candleIngestionService`, `superTrendService`, `rsiComputationService`,
`signalStateService`, `marketPulseService`. It completely omits Phase 5
(`candleRollupService.rollupForAllActiveAssets`) and Phase 6
(`w1IndicatorService.processAllActiveAssets`), which are the new code added in this phase.

As a result, someone could delete or reorder phases 5–6 in `runDaily` and this test would
still pass. The mock objects for `candleRollupService` and `w1IndicatorService` are declared
(lines 46–48) but never verified.

**Fix:** Extend the `InOrder` block to include all six phases:

```java
InOrder inOrder = inOrder(candleIngestionService, superTrendService, rsiComputationService,
        signalStateService, marketPulseService, candleRollupService, w1IndicatorService);
// ... existing verifications ...
inOrder.verify(candleRollupService).rollupForAllActiveAssets(eq(Timeframe.D1), eq(Timeframe.W1), any());
inOrder.verify(w1IndicatorService).processAllActiveAssets();
```

---

### WR-03: `MarketBreadthSnapshot` revision path silently no-ops instead of updating

**File:** `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/MarketPulseService.java:126-131`

**Issue:** When a snapshot already exists and its values differ from what the recomputed data
would produce (`isSame` returns false), the `upsertSnapshot` method logs a REVISION warning
but does nothing — the comment says "we can just replace or update" but no update code is
present (lines 128-131). The snapshot on disk keeps stale values.

This means a pipeline re-run after a D1 or W1 indicator revision will not correct the
breadth snapshot. The warning message creates an illusion that a problem was detected and
handled, when in fact it is silently swallowed.

**Fix:** Add an update path. Since `MarketBreadthSnapshot` may lack setters, the simplest
safe approach is delete-then-insert:

```java
} else {
    log.warn("REVISION: MarketPulse snapshot changed for {}. Replacing.", snapshot.getSnapshotCloseTime());
    snapshotRepository.delete(existing);
    snapshotRepository.save(snapshot);
}
```

---

### WR-04: `W1ApiIntegrationTest` builds `MockMvc` without security filter chain; may mask auth failures

**File:** `backend/java/src/test/java/walshe/projectcolumbo/api/v1/W1ApiIntegrationTest.java:79`

**Issue:** `MockMvcBuilders.webAppContextSetup(context).build()` initialises MockMvc without
applying the full security filter chain (`.apply(springSecurity())`). If the application adds
Spring Security in future, all endpoints in this test will appear to accept unauthenticated
requests, silently passing tests that should actually enforce authentication. This is a
latent reliability problem, not currently broken.

Additionally, the `mockMvc` field is an instance variable initialised in `@BeforeEach` but
never explicitly scoped to be thread-safe. Since `@SpringBootTest` with `RANDOM_PORT` can
share the same application context across test methods run in parallel, this is acceptable
for the current serial test runner but fragile if parallelism is enabled later.

**Fix:** Apply the security filter chain explicitly:

```java
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

mockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply(springSecurity())
        .build();
```

If no security is configured yet, add a comment explaining this is intentionally omitted and
will need adding when security is introduced.

---

## Info

### IN-01: `MarketPipelineServiceTest.runDaily_shouldStopOnFailure` does not verify Phase 5/6 are also skipped

**File:** `backend/java/src/test/java/walshe/projectcolumbo/ingestion/MarketPipelineServiceTest.java:99-113`

**Issue:** Line 109 verifies `verifyNoInteractions(superTrendService, rsiComputationService, signalStateService, marketPulseService)` but does not include `candleRollupService` or `w1IndicatorService`. If either of those were accidentally called before the exception propagated, the test would not catch it.

**Fix:** Add the new services to the no-interaction assertion:

```java
verifyNoInteractions(superTrendService, rsiComputationService, signalStateService,
        marketPulseService, candleRollupService, w1IndicatorService);
```

---

### IN-02: `IngestionController` is not covered by any test in the reviewed set

**File:** `backend/java/src/main/java/walshe/projectcolumbo/api/v1/IngestionController.java`

**Issue:** No test exercises `POST /api/v1/internal/ingestion/run`. The W1 API integration
test covers market-pulse, signals, and scan endpoints but not the trigger endpoint.
The CONFLICT path (`IngestionAlreadyRunningException` → 409) and the error body shape
("STARTED" vs actual status) are both untested.

**Fix:** Add at least a happy-path and a 409 conflict test in `W1ApiIntegrationTest` or a
dedicated `IngestionControllerTest`.

---

_Reviewed: 2026-05-22T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
