---
phase: 01-w1-candle-derivation
verified: 2026-05-21T10:00:00Z
status: human_needed
score: 8/8 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Run the full backend test suite — cd backend/java && ./mvnw test"
    expected: "BUILD SUCCESS, 0 failures, 0 errors; CandleRollupServiceTest (5 tests) and CandleRollupIntegrationTest (3 tests) both appear and pass; no 'ALTER TYPE ... ADD VALUE cannot run inside a transaction block' error"
    why_human: "Test suite requires a running Docker daemon (Testcontainers spins up a real PostgreSQL 16 container). Cannot execute in the verifier process without Docker access."
---

# Phase 1: W1 Candle Derivation Verification Report

**Phase Goal:** Implement a generic timeframe rollup service that derives complete W1 candles from D1 candles and persists them in the existing candle table.
**Verified:** 2026-05-21T10:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| #  | Truth | Status | Evidence |
|----|-------|--------|----------|
| 1  | W1 is a valid value of the PostgreSQL `timeframe` enum after migration V13 runs | VERIFIED | `V13__add_w1_timeframe.sql` contains `ALTER TYPE timeframe ADD VALUE IF NOT EXISTS 'W1';` — no transaction directives, mirrors V8 precedent |
| 2  | W1 is a valid constant of the Java `Timeframe` enum with string value `"1W"` | VERIFIED | `Timeframe.java` line 7: `D1("1D"), W1("1W");` — exact constant with correct value |
| 3  | Existing D1 candle rows and the D1 enum value are unaffected by the migration | VERIFIED | Migration is additive-only (`ADD VALUE IF NOT EXISTS`); `fromValue()` iterates `values()` generically — no D1 code touched |
| 4  | Given seven D1 candles for a full Mon-Sun UTC week, the service produces one rolled-up candle with correct OHLCV | VERIFIED | `CandleRollupService.java` lines 116–139: open=first.open, high=max, low=min, close=last.close, volume=sum; `rollup_producesCorrectOHLCV` unit test asserts all five values |
| 5  | An incomplete current week produces no rolled-up candle | VERIFIED | Lines 94–105: completeness guard requires `weekCandles.size() == 7` AND `lastCandle.closeTime.isBefore(boundary)`; `rollup_skipIncompleteWeek` unit test verifies 5-candle week produces no save call |
| 6  | The rollup logic is parameterized by source Timeframe, target Timeframe, and week-start DayOfWeek — no D1 or W1 literal in the grouping or aggregation logic | VERIFIED | `grep -E "Timeframe\.(D1|W1)"` on `CandleRollupService.java` returns zero matches; `weekStartDay` parameter drives `TemporalAdjusters.previousOrSame(weekStartDay)` (line 76); `rollup_isParameterized` unit test confirms |
| 7  | Weeks already stored at the target timeframe are skipped (incremental detection) | VERIFIED | Lines 62–69 fetch `findLatestCloseTime`; lines 108–113 skip weeks where `!weekCloseTime.isAfter(lastStoredCloseTime.get())`; `rollup_skipsAlreadyStoredWeeks` unit test confirms |
| 8  | A real PostgreSQL database accepts W1 as a valid timeframe enum value; rollup is idempotent and incremental across runs | VERIFIED (code only) | `CandleRollupIntegrationTest.java` 177 lines, three test methods (`w1_timeframeExistsInDb`, `rollup_isIdempotent`, `rollup_isIncremental`) wired to a live `@SpringBootTest + @Import(TestcontainersConfiguration.class)` context; test OHLCV assertions are substantive — runtime pass requires human confirmation below |

**Score:** 8/8 truths verified at code level

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/java/src/main/resources/db/migration/V13__add_w1_timeframe.sql` | Flyway migration extending timeframe enum with W1 | VERIFIED | 2 lines; contains `ALTER TYPE timeframe ADD VALUE IF NOT EXISTS 'W1'`; no BEGIN/COMMIT |
| `backend/java/src/main/java/walshe/projectcolumbo/persistence/model/Timeframe.java` | W1 enum constant | VERIFIED | Line 7: `D1("1D"), W1("1W");`; `fromValue()` unchanged and generic |
| `backend/java/src/main/java/walshe/projectcolumbo/rollup/CandleRollupService.java` | Generic rollup service | VERIFIED | 190 lines (min_lines: 90 satisfied); annotated `@Service`, `@Transactional`; `rollupForAllActiveAssets` present; no Timeframe literals |
| `backend/java/src/test/java/walshe/projectcolumbo/rollup/CandleRollupServiceTest.java` | Unit tests for OHLCV correctness, incomplete-week guard, and genericity | VERIFIED | 239 lines; all 5 required test methods present; uses `@Mock`, `MockitoAnnotations`, `ArgumentCaptor<Candle>` |
| `backend/java/src/test/java/walshe/projectcolumbo/rollup/CandleRollupIntegrationTest.java` | Testcontainers integration tests | VERIFIED | 177 lines (min_lines: 80 satisfied); `@SpringBootTest + @Import(TestcontainersConfiguration.class)`; 3 required test methods present; substantive OHLCV assertions |
| `backend/java/src/test/java/walshe/projectcolumbo/persistence/model/TimeframeTest.java` | JUnit 5 unit test for Timeframe enum | VERIFIED | File exists at correct path |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `Timeframe.java` | `candle.timeframe` DB column | `@Enumerated(EnumType.STRING) + @JdbcTypeCode(SqlTypes.NAMED_ENUM)` on `Candle.timeframe` | VERIFIED | `W1("1W")` present; existing JPA wiring in `Candle.java` handles enum-NAME persistence — no change needed |
| `CandleRollupService` | `CandleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc` | constructor-injected repository | VERIFIED | Line 55: `candleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(asset, sourceTimeframe)` |
| `CandleRollupService` | `CandleRepository.findByAssetAndTimeframeAndCloseTime` | idempotency check | VERIFIED | Line 142: `candleRepository.findByAssetAndTimeframeAndCloseTime(asset, targetTimeframe, weekCloseTime)` |
| `CandleRollupService` | `AssetRepository.findByActiveTrue` | per-asset loop in `rollupForAllActiveAssets` | VERIFIED | Line 41: `assetRepository.findByActiveTrue()` |
| `CandleRollupIntegrationTest` | `CandleRollupService` (real Spring bean) | `@Autowired` against `@SpringBootTest` context | VERIFIED | Lines 29: `@Autowired private CandleRollupService candleRollupService;` |
| `CandleRollupIntegrationTest` | candle table (W1 rows) | `candleRepository.findByAssetAndTimeframe(asset, Timeframe.W1)` after rollup | VERIFIED | Lines 90, 128, 151, 164: all assertion queries use `Timeframe.W1` |

---

### Data-Flow Trace (Level 4)

`CandleRollupService` is a service (not a rendering component) — data-flow trace applies to its DB write path rather than a UI render.

| Step | Data Variable | Source | Produces Real Data | Status |
|------|---------------|--------|--------------------|--------|
| Source fetch | `sourceCandles` | `candleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc` | Real DB query (Spring Data) | FLOWING |
| Incremental guard | `lastStoredCloseTime` | `candleRepository.findLatestCloseTime(asset.getId(), targetTimeframe.name())` | Real DB query (native) | FLOWING |
| Week grouping | `weekGroups` | `sourceCandles.stream().collect(groupingBy(...))` | Derived from real fetch | FLOWING |
| OHLCV aggregation | `targetCandle` | Stream reduction over `weekCandles` (open, high, low, close, volume) | No hardcoded fallbacks in aggregation path | FLOWING |
| Idempotent upsert | `candleRepository.save(targetCandle)` | `findByAssetAndTimeframeAndCloseTime` + `ifPresentOrElse` | Real DB write | FLOWING |

No hollow props, static returns, or disconnected data paths detected.

---

### Behavioral Spot-Checks

Testcontainers tests require Docker — cannot execute without a running Docker daemon. Deferred to human verification below.

Unit-test compilation and structure can be spot-checked structurally:

| Behavior | Check | Result | Status |
|----------|-------|--------|--------|
| No Timeframe literals in service | `grep -E "Timeframe\.(D1|W1)" CandleRollupService.java` | Zero matches | PASS |
| `rollupForAllActiveAssets` exists and is `@Transactional` | File read lines 39–51 | Present and annotated | PASS |
| `TemporalAdjusters.previousOrSame` used (not epoch modulo) | `grep TemporalAdjusters.previousOrSame` | Line 76 | PASS |
| V13 migration has no transaction wrappers | `grep -c "BEGIN\|COMMIT"` | 0 | PASS |
| All 5 unit test methods present | `grep void rollup_` | 5 methods found | PASS |
| All 3 integration test methods present | `grep void` | 3 methods found | PASS |
| All 6 documented commits exist | `git log --oneline` | All hashes confirmed | PASS |
| Line counts meet minimums (90 / 80) | `wc -l` | 190 / 177 | PASS |

---

### Probe Execution

No `scripts/*/tests/probe-*.sh` files declared or found. Step 7c: SKIPPED (no probes defined for this phase).

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| CNDL-01 | 01-02-PLAN.md | System derives W1 candles by rolling up D1 candles (Monday open → Sunday close, UTC week boundaries) | SATISFIED | `CandleRollupService` groups by `TemporalAdjusters.previousOrSame(weekStartDay)`, open=Mon open, close=Sun close; `rollup_producesCorrectOHLCV` validates |
| CNDL-02 | 01-02-PLAN.md | Partial weeks (current incomplete week) not stored | SATISFIED | Completeness guard: `weekCandles.size() != 7` and `!lastCandle.closeTime.isBefore(boundary)`; `rollup_skipIncompleteWeek` validates |
| CNDL-03 | 01-03-PLAN.md | Rollup is incremental — only derives new W1 candles from D1 candles added since last rollup | SATISFIED (runtime pending) | `findLatestCloseTime` + strictly-after guard implemented; `rollup_isIncremental` integration test validates this end-to-end against real DB (runtime confirmation needed) |
| CNDL-04 | 01-02-PLAN.md | Rollup mechanism is timeframe-generic (no hardcoded D1→W1 literals) | SATISFIED | Zero `Timeframe.D1` or `Timeframe.W1` references in `CandleRollupService.java`; signature is `(Timeframe sourceTimeframe, Timeframe targetTimeframe, DayOfWeek weekStartDay)` |
| CNDL-05 | 01-01-PLAN.md, 01-03-PLAN.md | DB schema supports W1 as a valid Timeframe value | SATISFIED (runtime pending) | `V13__add_w1_timeframe.sql` and `Timeframe.java` W1 constant both exist; `w1_timeframeExistsInDb` integration test confirms DB round-trip (runtime confirmation needed) |

All 5 phase-1 requirement IDs (CNDL-01 through CNDL-05) are claimed by a plan and have observable code evidence. No orphaned requirements.

**ROADMAP Success Criteria mapping:**

| SC | Description | Status |
|----|-------------|--------|
| SC-1 | W1 exists as a valid Timeframe enum value and in the DB enum | VERIFIED |
| SC-2 | Full-week rollup produces correct OHLCV candle | VERIFIED |
| SC-3 | Incomplete weeks produce no W1 candle | VERIFIED |
| SC-4 | Running rollup twice produces no duplicate (idempotent upsert) | VERIFIED (runtime pending) |
| SC-5 | Rollup component parameterized by source/target Timeframe — no D1/W1 literals in core logic | VERIFIED |

---

### Anti-Patterns Found

Scanned files: `CandleRollupService.java`, `Timeframe.java`, `CandleRollupServiceTest.java`, `CandleRollupIntegrationTest.java`.

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| None | — | — | — | — |

No `TBD`, `FIXME`, `XXX`, `TODO`, `HACK`, `PLACEHOLDER`, or empty stub implementations found in any phase-modified file. No hardcoded empty data arrays or hollow props detected.

---

### Human Verification Required

#### 1. Full Backend Test Suite

**Test:** From the project root, run: `cd backend/java && ./mvnw test`

**Expected:**
- BUILD SUCCESS
- `Tests run: N, Failures: 0, Errors: 0, Skipped: 0` (SUMMARY claims 146 tests)
- `CandleRollupServiceTest` (5 tests) and `CandleRollupIntegrationTest` (3 tests) both appear in Surefire output and pass
- No `ALTER TYPE ... ADD VALUE cannot run inside a transaction block` error in build output
- Flyway log shows V13 applied without error

**Why human:** Testcontainers integration tests require a live Docker daemon to spin up a real PostgreSQL 16 container. The verifier cannot execute these tests in its process. This is the only path to confirming CNDL-03 (idempotency + incremental) and CNDL-05 (DB enum validity) at runtime.

---

### Gaps Summary

No gaps found. All 8 observable truths are satisfied by the actual codebase. All 5 required artifacts exist, are substantive (above minimum line counts), and are wired to their dependencies. All 6 documented commits exist in git history. No debt markers or anti-patterns detected.

The single human verification item is a test-execution confirmation, not a gap — the code implementing the behavior is complete and correct at the static analysis level.

---

_Verified: 2026-05-21T10:00:00Z_
_Verifier: Claude (gsd-verifier)_
