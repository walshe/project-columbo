---
phase: 02-w1-indicators-signals
verified: 2026-05-21T18:00:00Z
status: human_needed
score: 6/6 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Run full Maven integration test suite end-to-end against a live Testcontainers instance"
    expected: "All 153 tests pass including 6 W1IndicatorPipelineIntegrationTest methods; BUILD SUCCESS reported"
    why_human: "Integration tests require Docker/Testcontainers to be available and running. Cannot execute in this environment without a live Docker daemon."
deferred:
  - truth: "W1IndicatorService.processAllActiveAssets() is wired into MarketPipelineService.runDaily()"
    addressed_in: "Phase 3"
    evidence: "Phase 3 goal: 'Integrate W1 derivation, indicator computation, and signal detection into the daily pipeline run'. Phase 3 PIPE-01 success criteria: 'A scheduled or manually triggered pipeline run produces W1 candles, indicators, signals, and market breadth without any manual intervention'."
---

# Phase 2: W1 Indicators & Signals Verification Report

**Phase Goal:** Compute SuperTrend and RSI on W1 candles for all active assets, detect weekly signal states, and produce weekly market breadth snapshots.
**Verified:** 2026-05-21T18:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| #  | Truth | Status | Evidence |
|----|-------|--------|----------|
| 1  | `MarketPulseService` exposes a public `computeForTimeframe(Timeframe)` method | VERIFIED | Method at line 40 of `MarketPulseService.java`; `@Transactional`; grep count = 1 |
| 2  | `computeDaily()` delegates to `computeForTimeframe(Timeframe.D1)` with no other behaviour change | VERIFIED | Line 51: `computeForTimeframe(Timeframe.D1);`; `@Transactional` retained; grep confirmed |
| 3  | After W1 pass, SuperTrend rows exist in `indicator_supertrend` for W1 (INDC-01) | VERIFIED | `supertrend_W1_isComputed` test in `W1IndicatorPipelineIntegrationTest` calls `superTrendService.processAllActiveAssets(Timeframe.W1, 10, new BigDecimal("2.0"), false)` and asserts `isNotEmpty()` |
| 4  | After W1 pass, RSI rows exist in `indicator_rsi` for W1 (INDC-02) | VERIFIED | `rsi_W1_isComputed` test calls `rsiComputationService.computeForActiveAssets(Timeframe.W1, 14, false)` and asserts W1 rows non-empty |
| 5  | Re-running W1 indicator pass adds no new rows — idempotent (INDC-03) | VERIFIED | `indicators_W1_areIdempotent` test runs SuperTrend + RSI W1 twice; asserts `count()` unchanged; `fullRecalc=false` confirmed in both `W1IndicatorService` calls |
| 6  | Signal state rows exist for W1 per active asset (SGNL-01) | VERIFIED | `signalState_W1_isDetected` test runs SuperTrend W1 -> RSI W1 -> `detectDaily()`; asserts `signalStateRepository.findAll()` filtered to `Timeframe.W1` is not empty |
| 7  | A `market_breadth_snapshot` row exists for W1 (SGNL-02) | VERIFIED | `marketBreadth_W1_isComputed` test runs full chain -> `computeForTimeframe(Timeframe.W1)`; asserts `marketBreadthSnapshotRepository.findAll()` filtered to `Timeframe.W1` is not empty |
| 8  | A single orchestrator sequences SuperTrend -> RSI -> signals -> breadth for W1 | VERIFIED | `W1IndicatorService.processAllActiveAssets()` (lines 53-73) executes 4 steps in strict serial order; `orchestrator_processAllActiveAssets_producesAllOutputs` test calls it once and asserts all 4 W1 output tables populated |

**Score:** 6/6 must-haves verified (all 8 observable truths verified — 6 match plan must_haves; 2 are roadmap success criteria expansions)

---

### Deferred Items

Items not yet met but explicitly addressed in later milestone phases.

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | `W1IndicatorService.processAllActiveAssets()` wired into `MarketPipelineService.runDaily()` | Phase 3 | Phase 3 goal: "Integrate W1 derivation, indicator computation, and signal detection into the daily pipeline run." PIPE-01 SC: pipeline run produces W1 outputs without manual intervention. |

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/MarketPulseService.java` | Timeframe-parameterized market breadth aggregation entry point; contains `computeForTimeframe` | VERIFIED | 144 lines; contains `computeForTimeframe` at line 40; `@Transactional` on both public methods; `computeDaily()` delegates via `Timeframe.D1` |
| `backend/java/src/main/java/walshe/projectcolumbo/marketpulse/W1IndicatorService.java` | W1 indicator/signal/breadth orchestration entry point for Phase 3; contains `processAllActiveAssets`; min 25 lines | VERIFIED | 74 lines (>25); `@Service`; `@Transactional processAllActiveAssets()`; 4 injected services |
| `backend/java/src/test/java/walshe/projectcolumbo/persistence/service/W1IndicatorPipelineIntegrationTest.java` | End-to-end W1 integration coverage; contains `supertrend_W1_isComputed`; min 80 lines | VERIFIED | 179 lines (>80); 6 `@Test` methods; all 6 named test methods present; `@Import(TestcontainersConfiguration.class)` |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `MarketPulseService.computeDaily()` | `MarketPulseService.computeForTimeframe(Timeframe)` | delegation with `Timeframe.D1` | WIRED | Line 51: `computeForTimeframe(Timeframe.D1);` — grep count = 1 |
| `MarketPulseService.computeForTimeframe(Timeframe)` | `computePulseForIndicator(timeframe, type)` | loop over `IndicatorType.values()` | WIRED | Lines 43-45: `for (IndicatorType type : IndicatorType.values()) { computePulseForIndicator(timeframe, type); }` |
| `W1IndicatorService` | `SuperTrendService.processAllActiveAssets` | ordered call with `Timeframe.W1` | WIRED | Line 56: `superTrendService.processAllActiveAssets(Timeframe.W1, 10, new BigDecimal("2.0"), false)` |
| `W1IndicatorService` | `MarketPulseService.computeForTimeframe` | final orchestration step | WIRED | Line 71: `marketPulseService.computeForTimeframe(Timeframe.W1)` |
| `W1IndicatorPipelineIntegrationTest.orchestrator_processAllActiveAssets_producesAllOutputs` | `W1IndicatorService.processAllActiveAssets()` | test calls `w1IndicatorService.processAllActiveAssets()` and asserts all 4 W1 output tables | WIRED | Line 166: single call; 4 assertions on SuperTrend, RSI, signal state, breadth W1 rows |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `MarketPulseService.computeForTimeframe` | `latestStates` (signal states) | `signalStateRepository.findLatestFinalizedForActiveAssets(timeframe, indicatorType, boundary)` — JPQL query confirmed in `SignalStateRepository.java` lines 54-68 | Yes — DB query with `@Query` annotation | FLOWING |
| `W1IndicatorService.processAllActiveAssets` | No direct data rendering — thin orchestrator delegates to 4 services | N/A (control flow only) | N/A | N/A — orchestrator |

---

### Behavioral Spot-Checks

Runnable check without live Docker is not feasible for integration tests. Structural checks performed instead:

| Behavior | Check | Result | Status |
|----------|-------|--------|--------|
| `computeForTimeframe` method exists in MarketPulseService | `grep -c 'public void computeForTimeframe(Timeframe'` | 1 | PASS |
| `computeDaily()` delegates with `Timeframe.D1` | `grep -c 'computeForTimeframe(Timeframe.D1)'` | 1 | PASS |
| Hardcoded `Timeframe timeframe = Timeframe.D1` removed from method body | grep on non-comment lines | 0 | PASS |
| Both methods `@Transactional` | grep -B1 'public void compute' | Both preceded by `@Transactional` | PASS |
| `W1IndicatorService` annotated `@Service` | `grep -c '@Service'` | 1 | PASS |
| `processAllActiveAssets` calls all 4 services in correct order | inspect lines 56, 61, 66, 71 | SuperTrend -> RSI -> detectDaily -> computeForTimeframe(W1) | PASS |
| `fullRecalc=false` enforced | inspect call parameters | Both calls use `false` as last argument | PASS |
| Integration test has 6 `@Test` methods | `grep -c '@Test'` | 6 | PASS |
| All 6 test method names present | grep for all 6 | 6 | PASS |
| Orchestrator test uses only `w1IndicatorService.processAllActiveAssets()` | inspect method body | Single call; no direct underlying service calls | PASS |

---

### Probe Execution

No declared probes in PLAN files. No `scripts/*/tests/probe-*.sh` files found for this phase. Step 7c: SKIPPED (no probes declared or discoverable).

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| INDC-01 | 02-02-PLAN.md | SuperTrend (ATR 10, multiplier 2.0) computed on W1 candles for all active assets | SATISFIED | `supertrend_W1_isComputed` test; `W1IndicatorService` calls `processAllActiveAssets(Timeframe.W1, 10, new BigDecimal("2.0"), false)` |
| INDC-02 | 02-02-PLAN.md | RSI (period 14, Wilder's smoothing) computed on W1 candles for all active assets | SATISFIED | `rsi_W1_isComputed` test; `W1IndicatorService` calls `computeForActiveAssets(Timeframe.W1, 14, false)` |
| INDC-03 | 02-02-PLAN.md | Indicator computation on W1 is incremental — only new candles, not full recalculation | SATISFIED | `indicators_W1_areIdempotent` test asserts row counts unchanged after second run; `fullRecalc=false` in both service calls |
| SGNL-01 | 02-01-PLAN.md, 02-02-PLAN.md | Signal state (BULLISH/BEARISH/UNKNOWN + cross events) detected on W1 | SATISFIED | `signalState_W1_isDetected` test; `detectDaily()` iterates `Timeframe.values()` including W1 |
| SGNL-02 | 02-01-PLAN.md, 02-02-PLAN.md | Market breadth snapshot computed for W1 | SATISFIED | `marketBreadth_W1_isComputed` test; `computeForTimeframe(Timeframe.W1)` exists and routes to DB-backed `findLatestFinalizedForActiveAssets` |

**Orphaned requirements check:** All 5 Phase 2 requirements (INDC-01/02/03, SGNL-01/SGNL-02) are claimed by at least one plan. No orphaned requirements found.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `MarketPulseService.java` | 126-129 | Incomplete snapshot-revision update path (`else` branch of `upsertSnapshot` logs but does not update snapshot fields) | INFO | Pre-existing code (present in commit `9e3eea1`, before Phase 2). Phase 2 only added `computeForTimeframe` and made `computeDaily` delegate. This branch is not exercised in the normal happy path (snapshot same or new). No issue introduced by Phase 2. |

No `TBD`, `FIXME`, or `XXX` markers found in any Phase 2-modified files. No `TODO` markers in Phase 2-introduced code (`W1IndicatorService.java`, `W1IndicatorPipelineIntegrationTest.java`). Anti-pattern in `MarketPulseService.upsertSnapshot` is pre-existing and out of Phase 2 scope.

---

### Human Verification Required

#### 1. Full Integration Test Suite

**Test:** Run `cd backend/java && ./mvnw test` against a live Testcontainers/Docker environment.
**Expected:** BUILD SUCCESS; 153 tests pass with zero failures; `W1IndicatorPipelineIntegrationTest` reports 6/6 passing including `orchestrator_processAllActiveAssets_producesAllOutputs`.
**Why human:** Integration tests spin up a Testcontainers PostgreSQL instance. Cannot execute without a live Docker daemon. The SUMMARY.md claims 153/153 passing and four specific commits are verified in git, but actual test execution must be confirmed by a developer with Docker available.

---

### Gaps Summary

No blocking gaps. All 6 plan must-haves are verified against actual codebase. All 5 phase requirements have implementation evidence. All required artifacts exist at expected paths, meet minimum line counts, contain required method signatures, and are substantively implemented (not stubs). All key links are wired with correct call sites, ordering, and parameters.

The single item requiring human action is execution of the Testcontainers integration tests, which cannot be run in this verification environment.

One pre-existing warning in `MarketPulseService.upsertSnapshot` (lines 126-129) documents an incomplete snapshot-revision update path, but this code predates Phase 2 and is not on the hot path for the W1 requirements being verified.

---

_Verified: 2026-05-21T18:00:00Z_
_Verifier: Claude (gsd-verifier)_
