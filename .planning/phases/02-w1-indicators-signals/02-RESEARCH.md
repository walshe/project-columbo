# Phase 2: W1 Indicators & Signals - Research

**Researched:** 2026-05-21
**Domain:** Java Spring Boot — indicator computation, signal detection, market breadth snapshots on a new timeframe
**Confidence:** HIGH

---

## Summary

Phase 2 extends the existing D1 indicator pipeline to produce the same outputs on W1 candles.
The critical insight from reading every relevant service and entity is that **all four services
involved (SuperTrendService, RsiComputationService, SignalStateService, MarketPulseService) are
already fully timeframe-parameterized** — with one exception: `MarketPulseService.computeDaily()`
hardcodes `Timeframe.D1`. Every other service accepts a `Timeframe` argument and passes it
through to repository queries. This means the work to run indicators and signals on W1 is
additive call-site wiring, not a refactor of the services themselves.

The dependency ordering is strict and serial: W1 candles must exist before SuperTrend/RSI can
be computed; both SuperTrend and RSI W1 indicators must exist before W1 signals can be detected;
W1 signals must exist before the W1 market breadth snapshot can be computed. Phase 2 handles
steps 2–4 of this chain (W1 candles already exist after Phase 1). The pipeline entry point
(`MarketPipelineService.runDaily()`) does not need to change in Phase 2 — Phase 3 wires it all
together. Phase 2 validates each step in isolation via a standalone orchestration method or
direct service calls.

SuperTrend warmup is the most significant technical risk. The calculator needs at least
`atrLength` candles (10) to produce a first ATR value, and `atrLength * 10` candles (100) for
the incremental warm-up window. W1 candles go back only as far as the D1 candle history — if
D1 data is 12 months old, that is ~52 W1 candles, which exceeds the 10-candle ATR minimum but
falls short of the 100-candle incremental warm-up window. The incremental path still works
correctly in this scenario: the warm-up window clamps to `max(0, anchorIndex - 100)`, so it
uses whatever history is available, accepting a slightly less stable ATR for the first batch.

**Primary recommendation:** Implement `W1IndicatorService` as a thin orchestrator that calls
the existing `SuperTrendService`, `RsiComputationService`, `SignalStateService`, and an updated
`MarketPulseService` (new `computeForTimeframe(Timeframe)` method) with `Timeframe.W1` — zero
calculator changes needed.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| INDC-01 | SuperTrend (ATR 10, multiplier 2.0) computed on W1 candles for all active assets | `SuperTrendService.processAllActiveAssets(Timeframe.W1, 10, 2.0, false)` — already fully parameterized, zero changes to the service |
| INDC-02 | RSI (period 14, Wilder's smoothing) computed on W1 candles for all active assets | `RsiComputationService.computeForActiveAssets(Timeframe.W1, 14, false)` — already fully parameterized |
| INDC-03 | Indicator computation is incremental (no full recalculation) | Both services use `findFirstByAssetAndTimeframeOrderByCloseTimeDesc` as the anchor, then fetch only candles after that point; `fullRecalc=false` triggers this path |
| SGNL-01 | Signal state (BULLISH/BEARISH/UNKNOWN + cross events) detected on W1 | `SignalStateService.processAsset(asset, Timeframe.W1, false)` — already fully parameterized; iterates SUPERTREND and RSI indicator types |
| SGNL-02 | Market breadth snapshot computed for W1 | `MarketPulseService.computeDaily()` **hardcodes `Timeframe.D1`** — needs a new `computeForTimeframe(Timeframe)` method; the rest of the logic is unchanged |

</phase_requirements>

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| SuperTrend W1 computation | API / Backend | Database / Storage | Stateless calculator + JPA repository; no new tier needed |
| RSI W1 computation | API / Backend | Database / Storage | Same pattern as SuperTrend |
| W1 signal detection | API / Backend | Database / Storage | `SignalStateService.processAsset` already handles any `Timeframe` value |
| W1 market breadth snapshot | API / Backend | Database / Storage | `MarketPulseService` needs a timeframe-parameterized entry point |
| W1 indicator orchestration | API / Backend | — | New `W1IndicatorService` (or direct call in a test driver) wires all four steps in order |
| DB schema | Database / Storage | — | No new migrations required; all tables already use the `timeframe` enum and `W1` is now valid |

---

## Standard Stack

### Core (all already in pom.xml — no new dependencies)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot Data JPA | 4.0.2 | ORM / repository | Already used for all indicator/signal repositories |
| JUnit Jupiter + Testcontainers | managed by Spring Boot | Integration tests | `@SpringBootTest @Import(TestcontainersConfiguration.class)` pattern used in 10+ existing tests |
| Mockito 5.14.2 | in pom.xml | Unit test mocking | Used in `MarketPulseServiceTest`, `MarketPipelineServiceTest`, etc. |
| AssertJ | managed by Spring Boot | Fluent assertions | Used in all existing service tests |

**Installation:** No new packages. Phase 2 installs zero external dependencies.

[VERIFIED: codebase grep — pom.xml, all referenced test classes]

---

## Package Legitimacy Audit

No external packages are introduced in this phase. Section omitted.

---

## Architecture Patterns

### System Architecture Diagram

```
[W1 candles in candle table]          (produced by Phase 1 CandleRollupService)
         |
         v
SuperTrendService.processAllActiveAssets(W1, atr=10, mult=2.0, fullRecalc=false)
         |
         +---> per-asset: findByAssetAndTimeframeOrderByCloseTimeAsc(asset, W1)
         |                  |
         |             CandleFilters.finalizedBeforeUtcMidnightToday()
         |                  |
         |             SuperTrendCalculator.calculateIncremental(candles, 10, 2.0, lastStoredCloseTime, false)
         |                  |
         |             upsertResults() → indicator_supertrend (timeframe='W1')
         |
         v
RsiComputationService.computeForActiveAssets(W1, period=14, fullRecalc=false)
         |
         +---> per-asset: same pattern, → indicator_rsi (timeframe='W1')
         |
         v
SignalStateService.processAsset(asset, W1, fullRecalc=false)   [for each active asset]
         |
         +---> processAssetForIndicator(asset, W1, SUPERTREND, false)
         |          |
         |     superTrendRepository.findByAssetAndTimeframeAndCloseTimeAfterOrderByCloseTimeAsc(asset, W1, lastCloseTime)
         |          |
         |     SignalStateCalculator.calculate(indicators, previousDirection) → signal_state (timeframe='W1')
         |
         +---> processAssetForIndicator(asset, W1, RSI, false)
                   |
              rsiRepository.findByAssetAndTimeframeAndCloseTimeAfterOrderByCloseTimeAsc(asset, W1, lastCloseTime)
                   |
              SignalStateCalculator.calculateRsi(indicators, previousTrend) → signal_state (timeframe='W1')
         |
         v
MarketPulseService.computeForTimeframe(W1)   [NEW method — refactor of computeDaily()]
         |
         +---> for each IndicatorType in [SUPERTREND, RSI]:
                   |
              signalStateRepository.findLatestFinalizedForActiveAssets(W1, indicatorType, boundary)
                   |
              aggregate bullish/bearish/unknown counts
                   |
              upsertSnapshot(W1, indicatorType, ...) → market_breadth_snapshot (timeframe='W1')
```

### Recommended Project Structure

New file locations:

```
backend/java/src/main/java/walshe/projectcolumbo/
└── (no new packages for Phase 2)
    ├── marketpulse/
    │   └── MarketPulseService.java        MODIFY: add computeForTimeframe(Timeframe)
    └── (SuperTrendService, RsiComputationService, SignalStateService — UNCHANGED)

backend/java/src/test/java/walshe/projectcolumbo/
└── persistence/service/
    ├── W1SuperTrendIntegrationTest.java   NEW: INDC-01, INDC-03 (SuperTrend)
    ├── W1RsiIntegrationTest.java          NEW: INDC-02, INDC-03 (RSI)
    ├── W1SignalStateIntegrationTest.java  NEW: SGNL-01
    └── W1MarketPulseIntegrationTest.java  NEW: SGNL-02
```

Alternatively, a single `W1IndicatorPipelineIntegrationTest.java` can cover all five
requirements end-to-end using a seeded W1 candle fixture.

### Pattern 1: Calling Existing Services with W1 (no service changes for indicators/signals)

**What:** Pass `Timeframe.W1` to services that are already timeframe-parameterized.

**When to use:** INDC-01, INDC-02, SGNL-01 — all three services accept `Timeframe` as a
parameter and route it through to all repository queries.

```java
// Source: SuperTrendService.processAllActiveAssets [VERIFIED: codebase]
superTrendService.processAllActiveAssets(Timeframe.W1, 10, new BigDecimal("2.0"), false);

// Source: RsiComputationService.computeForActiveAssets [VERIFIED: codebase]
rsiComputationService.computeForActiveAssets(Timeframe.W1, 14, false);

// Source: SignalStateService.detectDaily() iterates Timeframe.values()
// Since W1 is now in Timeframe.values(), detectDaily() ALREADY covers W1.
// Alternatively call processAsset per asset with W1.
signalStateService.detectDaily();  // Now includes W1 automatically — see Pitfall 2
```

### Pattern 2: Extracting a Timeframe-Parameterized Market Pulse Method

**What:** Add a `computeForTimeframe(Timeframe)` method to `MarketPulseService` that
generalizes the existing `computeDaily()` logic (which hardcodes `Timeframe.D1`).

**When to use:** SGNL-02 — required because `computeDaily()` cannot produce W1 snapshots.

```java
// BEFORE (existing — D1 hardcoded):
@Transactional
public void computeDaily() {
    Timeframe timeframe = Timeframe.D1;  // hardcoded
    for (IndicatorType type : IndicatorType.values()) {
        computePulseForIndicator(timeframe, type);
    }
}

// AFTER (refactored — timeframe as parameter):
@Transactional
public void computeForTimeframe(Timeframe timeframe) {
    log.info("Starting MarketPulse aggregation for timeframe {}", timeframe);
    for (IndicatorType type : IndicatorType.values()) {
        computePulseForIndicator(timeframe, type);
    }
}

// computeDaily() delegates to avoid breaking callers:
@Transactional
public void computeDaily() {
    computeForTimeframe(Timeframe.D1);
}
```

[VERIFIED: codebase — MarketPulseService.java shows `computePulseForIndicator(timeframe, type)`
already accepts `Timeframe` as a parameter; only the loop entry point is hardcoded]

### Pattern 3: SignalStateService.detectDaily() and Timeframe.values() Interaction

**What:** `detectDaily()` iterates `Timeframe.values()` to cover all timeframes. Since
`Timeframe.W1` was added in Phase 1, calling `detectDaily()` after W1 indicators exist will
automatically process W1 signals.

**Critical precondition:** W1 indicators (both SuperTrend and RSI) must be persisted **before**
`detectDaily()` is called. If called with no W1 indicators, it produces `UNKNOWN` signal states
for W1 (the "Case A" branch in `processAssetForIndicator`) — which is correct behavior.

```java
// Source: SignalStateService.detectDaily() [VERIFIED: codebase]
for (Timeframe timeframe : Timeframe.values()) {  // now iterates D1 and W1
    ProcessingStats stats = this.processAsset(asset, timeframe, false);
    ...
}
```

### Pattern 4: Idempotent Upsert in Repositories (no changes needed)

All three indicator/signal repositories have a `UNIQUE` constraint keyed on
`(asset_id, timeframe, close_time)` for indicators and
`(asset_id, timeframe, indicator_type, close_time)` for signal states. The services already use
`findBy...AndCloseTime` + `ifPresentOrElse` (SuperTrend) or `existing.isPresent()` checks
(RSI, SignalState) before saving. Re-running any service for W1 is safe.

[VERIFIED: codebase — V2, V9, V3 migrations; SuperTrendService, RsiComputationService,
SignalStateService upsert logic]

### Pattern 5: SuperTrend ATR Warmup for Sparse W1 History

**What:** `SuperTrendCalculator.calculateIncremental` uses a warmup window of `atrLength * 10 =
100` candles before the anchor point. If total W1 history is fewer than 100 candles, the code
uses `max(0, anchorIndex - 100)` which clamps to index 0 — safe, but ATR stabilization is less
complete.

**When to use:** First-run W1 SuperTrend computation on an asset with limited history (< 100
weeks ≈ < 2 years of D1 data).

```java
// Source: SuperTrendCalculator.calculateIncremental [VERIFIED: codebase]
int start = Math.max(0, anchorIndex - (atrLength * 10));
List<Candle> window = candles.subList(start, candles.size());
```

**Implication for tests:** W1 integration tests must seed at least 11 W1 candles (ATR period 10
+ 1) to get any SuperTrend results. The D1 test in `SuperTrendServiceTest` uses 20 candles and
expects 11 results — mirror this for W1.

### Anti-Patterns to Avoid

- **Calling `SignalStateService.detectDaily()` before W1 indicators exist:** Produces UNKNOWN
  signal states for W1 for all assets. Always run SuperTrend and RSI first.
- **Forgetting `MarketPulseService.computeForTimeframe(W1)` after `detectDaily()`:** The breadth
  snapshot is not produced automatically; it requires an explicit call.
- **Modifying calculator classes (SuperTrendCalculator, RsiCalculator, SignalStateCalculator):**
  They are already timeframe-agnostic. No changes needed.
- **Adding a new `Timeframe.D1`-only guard in `computeForTimeframe`:** The private
  `computePulseForIndicator` is already timeframe-agnostic once the `Timeframe.D1` literal is
  removed from the `computeDaily()` caller.
- **Using `fullRecalc=true` for W1 in normal operation:** This forces recomputation of all
  historical indicators on every run. Use `fullRecalc=false` (INDC-03).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SuperTrend incremental computation | Custom ATR state machine | `SuperTrendCalculator.calculateIncremental(candles, 10, 2.0, lastStoredCloseTime, false)` | Existing implementation with warmup window handles ATR continuity correctly |
| RSI Wilder's smoothing | Custom EMA | `RsiCalculator.calculate(candles, 14)` + `RsiComputationService` | Already implements correct Wilder's smoothing with proper seed averaging |
| Signal flip detection | Manual prev/current comparison | `SignalStateCalculator.calculate(indicators, previousDirection)` | Handles BULLISH_REVERSAL, BEARISH_REVERSAL, CROSSED_ABOVE_60, CROSSED_BELOW_40 events |
| Breadth ratio calculation | Custom percentage math | `MarketPulseService.computePulseForIndicator(timeframe, type)` | Handles edge cases (zero present assets, missing vs unknown) correctly |
| Upsert idempotency | `ON CONFLICT DO UPDATE` SQL | Existing `findBy...AndCloseTime` + `ifPresentOrElse` pattern | Consistent with existing code; Hibernate manages optimistic locking |

---

## Common Pitfalls

### Pitfall 1: SignalStateService.detectDaily() Already Covers W1 After Phase 1

**What goes wrong:** A developer writes a new `detectW1()` method that duplicates the
`detectDaily()` loop but for W1 only, not realising `detectDaily()` already iterates
`Timeframe.values()` which includes `W1`.

**Why it happens:** `detectDaily()` has "Daily" in its name but its body is not D1-specific.

**How to avoid:** Call `detectDaily()` after both D1 and W1 indicators are computed. It covers
all `Timeframe.values()` in one pass.

**Warning signs:** Duplicate `SignalState` rows or `UNIQUE constraint violation` on
`unique_signal_state_asset_timeframe_indicator_close` — indicates two code paths each trying
to insert the same W1 signal.

### Pitfall 2: MarketPulseService.computeDaily() is the Only Hardcoded Timeframe

**What goes wrong:** After correctly running W1 indicators and signals, `computeDaily()` is
called expecting it to produce W1 breadth — it does not. Only a D1 snapshot is created.

**Why it happens:** `computeDaily()` sets `Timeframe timeframe = Timeframe.D1` before the loop.

**How to avoid:** Refactor `computeDaily()` to delegate to a new `computeForTimeframe(Timeframe)`
method. Call `computeForTimeframe(Timeframe.W1)` explicitly after W1 signal detection.

**Warning signs:** `marketBreadthSnapshotRepository.findByTimeframeAndIndicatorType(W1, ...)` 
returns empty even though W1 signals exist.

### Pitfall 3: Minimum Candle Count for SuperTrend and RSI

**What goes wrong:** An asset has fewer W1 candles than the indicator period requires, causing
the service to log "Not enough finalized candles" and skip — producing no W1 indicator rows for
that asset.

**Why it happens:**
- RSI requires `period + 1` candles minimum (15 for RSI-14). An asset with ≤ 14 W1 candles
  produces no RSI values (checked by `finalizedCandles.size() <= period` in
  `RsiComputationService.computeForAsset`).
- SuperTrend requires `atrLength` candles (10) for the first ATR. Fewer than 10 W1 candles
  yields all-null ATR results, so `upsertResults` stores nothing.

**How to avoid:** This is expected behavior for new assets with short W1 history. Integration
tests must seed at least 15 W1 candles (RSI needs 15; SuperTrend needs 10) to verify non-empty
output. Log "Not enough finalized candles" is a warning, not an error.

**Warning signs:** `indicator_supertrend` and `indicator_rsi` tables are empty for W1 timeframe
after calling the services. Check asset W1 candle count.

### Pitfall 4: W1 SignalState.detectDaily() Produces UNKNOWN States on First Run

**What goes wrong:** If the developer calls `detectDaily()` when W1 indicators are absent (e.g.,
before running SuperTrend/RSI on W1), the "Case A" branch creates `UNKNOWN` signal states for
W1. Subsequent indicator computation then adds indicator rows, but the signal states already
have the wrong state for those close times.

**Why it happens:** `processAssetForIndicator` falls through to the "empty results" path when no
indicator data exists; it creates an `UNKNOWN` SignalState if a finalized W1 candle is present.

**How to avoid:** Always run indicators before signals. Strict ordering: SuperTrend W1 → RSI W1
→ `detectDaily()` (which covers W1 and D1 both).

**Warning signs:** `signal_state` has `trend_state = 'UNKNOWN'` rows for `timeframe = 'W1'`
when W1 indicator tables have rows for the same `asset_id` and `close_time`.

### Pitfall 5: CandleFilters Boundary Applies to W1 candles by close_time

**What goes wrong:** `CandleFilters.finalizedBeforeUtcMidnightToday` filters by `close_time <
UTC_midnight_today`. W1 candles close on Sunday at the time inherited from the last D1 candle
(e.g., `2025-01-12 23:59:59.999 UTC`). On a Monday run, this Sunday close_time is before UTC
midnight, so the W1 candle passes the filter correctly.

**Why it happens:** The same filter used for D1 works correctly for W1 because W1 candle
`close_time` is always a past Sunday timestamp (verified in Phase 1: close_time is never
recomputed, taken directly from the last D1 candle in the group).

**How to avoid:** No change needed. The existing filter is correct for W1.

**Warning signs:** None expected. If W1 candles are missing from indicator computation, verify
that `CandleFilters.finalizedBeforeUtcMidnightToday` is returning them — likely a data problem
in W1 candles, not a filter bug.

---

## Code Examples

### Calling SuperTrend and RSI on W1 (no service modifications)

```java
// Source: MarketPipelineService.runDaily() — same pattern for W1 [VERIFIED: codebase]
superTrendService.processAllActiveAssets(Timeframe.W1, 10, new BigDecimal("2.0"), false);
rsiComputationService.computeForActiveAssets(Timeframe.W1, 14, false);
```

### MarketPulseService Refactor

```java
// Source: MarketPulseService.computeDaily() — extract timeframe parameter [VERIFIED: codebase]
@Transactional
public void computeForTimeframe(Timeframe timeframe) {
    log.info("Starting MarketPulse aggregation for {}", timeframe);
    for (IndicatorType type : IndicatorType.values()) {
        computePulseForIndicator(timeframe, type);  // already accepts Timeframe
    }
}

@Transactional
public void computeDaily() {
    computeForTimeframe(Timeframe.D1);  // backward-compatible delegation
}
```

### Integration Test Seed Pattern (mirrors CandleRollupIntegrationTest)

```java
// Source: CandleRollupIntegrationTest.java fixture pattern [VERIFIED: codebase]
// Seed 15 W1 candles past enough for RSI-14 (needs 15) and SuperTrend-10 (needs 10)
// Use dates from at least 15 complete weeks in the past
OffsetDateTime weekBase = OffsetDateTime.of(2024, 1, 7, 23, 59, 59, 999_000_000, ZoneOffset.UTC); // first Sunday
for (int i = 0; i < 15; i++) {
    Candle w1 = new Candle();
    w1.setAsset(btc);
    w1.setTimeframe(Timeframe.W1);
    w1.setOpenTime(weekBase.minusDays(6).plusWeeks(i).withHour(0).withMinute(0).withSecond(0).withNano(0));
    w1.setCloseTime(weekBase.plusWeeks(i));
    w1.setOpen(new BigDecimal("40000"));
    w1.setHigh(new BigDecimal("41000"));
    w1.setLow(new BigDecimal("39000"));
    w1.setClose(new BigDecimal("40500"));
    w1.setVolume(new BigDecimal("1000"));
    w1.setSource(MarketProvider.BINANCE);
    candleRepository.save(w1);
}
```

### SuperTrendService Result Count Expectation

```java
// Source: SuperTrendServiceTest.shouldProcessAssetAndPersistResults [VERIFIED: codebase]
// 15 W1 candles: first ATR at index 9 (10th candle, 0-based), so 15 - 9 = 6 results
// (Candles 10–14 inclusive at 0-based index 9 through 14: produces 6 SuperTrend rows)
// Note: SuperTrendServiceTest with 20 candles expects 11 results (20 - 10 + 1 = 11)
// with 15: 15 - 10 + 1 = 6; exact count depends on atrLength
assertThat(superTrendRepository
    .findByAssetAndTimeframeOrderByCloseTimeAsc(btc, Timeframe.W1))
    .hasSize(6); // for 15 candles, atrLength=10
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| D1-only market pulse (`computeDaily()` hardcodes D1) | `computeForTimeframe(Timeframe)` + `computeDaily()` delegates | Phase 2 | Phase 3 can call `computeForTimeframe(W1)` in pipeline without further changes |
| D1-only pipeline scheduler | Pipeline scheduler still calls D1 only | Phase 3 will add W1 | Phase 2 validates W1 services in isolation; Phase 3 wires into pipeline |

**No deprecated patterns:** All services are on Spring Boot 4.0.2. No legacy API usage
in the target classes.

---

## Detailed Service Analysis: Timeframe Parameterization

### SuperTrendService

**Method:** `processAllActiveAssets(Timeframe timeframe, int atrLength, BigDecimal multiplier, boolean fullRecalc)`

**Timeframe parameterized:** YES — `timeframe` passed to:
- `candleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(asset, timeframe)`
- `superTrendRepository.findFirstByAssetAndTimeframeOrderByCloseTimeDesc(asset, timeframe)`
- `superTrendRepository.findByAssetAndTimeframeAndCloseTime(asset, timeframe, ...)`
- `SuperTrendIndicator.fromResult(asset, timeframe, result)`

**Change required for W1:** None — call with `Timeframe.W1`.

### RsiComputationService

**Method:** `computeForActiveAssets(Timeframe timeframe, int period, boolean fullRecalc)`

**Timeframe parameterized:** YES — `timeframe` passed to:
- `candleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(asset, timeframe)`
- `rsiRepository.findFirstByAssetAndTimeframeOrderByCloseTimeDesc(asset, timeframe)`
- `rsiRepository.findByAssetAndTimeframeAndCloseTime(asset, timeframe, ...)`
- `rsi.setTimeframe(timeframe)`

**Change required for W1:** None — call with `Timeframe.W1`.

**RSI incremental note:** The current implementation always calls `rsiCalculator.calculate(finalizedCandles, period)` (full recalculation) even in incremental mode, but only persists rows after `latestStored.closeTime`. This is correct and adequate for W1. The comment in the code acknowledges this ("For simplicity..."). No change needed.

### SignalStateService

**Method:** `detectDaily()` → iterates `Timeframe.values()` → `processAsset(asset, timeframe, false)`

**Timeframe parameterized:** YES — after Phase 1 added `W1` to `Timeframe.values()`, `detectDaily()` automatically covers W1 whenever called after W1 indicators exist.

**Change required for W1:** None in the service. The calling order matters.

**Repository methods used for W1:**
- `superTrendRepository.findByAssetAndTimeframeAndCloseTimeAfterOrderByCloseTimeAsc(asset, W1, lastCloseTime)`
- `superTrendRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(asset, W1)`
- `rsiRepository.findByAssetAndTimeframeAndCloseTimeAfterOrderByCloseTimeAsc(asset, W1, lastCloseTime)`
- `signalStateRepository.findFirstByAssetIdAndTimeframeAndIndicatorTypeOrderByCloseTimeDesc(assetId, W1, indicatorType)`
- `signalStateRepository.findByAssetAndTimeframeAndIndicatorTypeAndCloseTime(asset, W1, indicatorType, closeTime)`

All these methods exist and accept `Timeframe` — no new repository methods needed.

### MarketPulseService

**Method:** `computeDaily()` — hardcodes `Timeframe.D1`. The private `computePulseForIndicator(timeframe, type)` IS parameterized.

**Change required for W1:** Add `computeForTimeframe(Timeframe)` public method that iterates `IndicatorType.values()` and calls `computePulseForIndicator(timeframe, type)`. Refactor `computeDaily()` to delegate.

**Repository methods already support W1:**
- `signalStateRepository.findLatestFinalizedForActiveAssets(timeframe, indicatorType, boundary)` — JPQL query parameterized by `timeframe`
- `snapshotRepository.findByTimeframeAndIndicatorTypeAndSnapshotCloseTime(timeframe, type, time)` — parameterized

**Upsert gap:** The `upsertSnapshot` method in `MarketPulseService` has a known limitation — if a snapshot exists and values have changed, it logs a warning but does not update (the `else` branch of `isSame` check is empty). This is a pre-existing issue for D1 too. For Phase 2, this is acceptable since market breadth is append-only for each unique `(timeframe, indicatorType, snapshotCloseTime)`. [VERIFIED: codebase — MarketPulseService.upsertSnapshot line 116-127]

---

## Dependency Chain and Ordering Constraints

```
Phase 1 output (already done):
  W1 candles in candle table
       |
       v   MUST COME FIRST
Phase 2 Step 1: SuperTrend W1
  superTrendService.processAllActiveAssets(W1, 10, 2.0, false)
  → indicator_supertrend rows with timeframe='W1'
       |
       | (parallel-safe)
       v
Phase 2 Step 2: RSI W1
  rsiComputationService.computeForActiveAssets(W1, 14, false)
  → indicator_rsi rows with timeframe='W1'
       |
       v   MUST COME AFTER BOTH INDICATORS
Phase 2 Step 3: Signal Detection
  signalStateService.detectDaily()   (covers D1 and W1 both)
  → signal_state rows with timeframe='W1'
       |
       v   MUST COME AFTER SIGNALS
Phase 2 Step 4: Market Breadth W1
  marketPulseService.computeForTimeframe(W1)
  → market_breadth_snapshot rows with timeframe='W1'
```

Steps 1 and 2 (SuperTrend and RSI) can technically run in either order — they read from
`candle` and write to separate tables. In practice, run them sequentially to match existing
pipeline convention.

Step 3 (`detectDaily()`) iterates both D1 and W1 — this means it now implicitly runs D1 signal
detection again too. This is idempotent (existing D1 signals will be skipped), but adds runtime.
If minimizing runtime is important in Phase 2 tests, call `processAsset(asset, Timeframe.W1, false)` per asset instead. For production wiring (Phase 3), `detectDaily()` is the correct entry point.

---

## Open Questions (RESOLVED)

1. **Should Phase 2 introduce a `W1IndicatorService` orchestrator?**
   - What we know: The four step sequence (SuperTrend → RSI → Signals → Breadth) needs a
     single entry point for Phase 3 to call.
   - What's unclear: Whether to build this orchestrator in Phase 2 or defer it to Phase 3.
   - Recommendation: Build it in Phase 2. Name it `W1IndicatorService` or add a
     `runW1Pass()` method to `MarketPipelineService`. This gives Phase 3 a clean call site.
     The planner should decide — both are valid.
   - **RESOLVED: `W1IndicatorService` built in Plan 02-02 Task 2 with `processAllActiveAssets()` entry point.**

2. **Does `MarketPulseService.upsertSnapshot` need a real update path?**
   - What we know: The `else` branch (snapshot exists but values changed) is a stub with a
     `log.warn` and no field update. For D1, this is pre-existing tech debt.
   - What's unclear: Whether SGNL-02 acceptance requires handling the revision case.
   - Recommendation: Out of scope for Phase 2. The upsert stub is acceptable since
     `(timeframe, indicatorType, snapshotCloseTime)` is the unique key and we don't update
     historical snapshots.
   - **RESOLVED: Out of scope for Phase 2 — pre-existing upsert stub accepted per INDC-03 acceptance criteria and Assumption A2.**

---

## Environment Availability

Step 2.6: SKIPPED (no new external dependencies — all tools already in use by Phase 1).

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker | Testcontainers integration tests | Assumed yes (Phase 1 tests passed) | — | — |
| Maven wrapper (mvnw) | Build + test | Confirmed (Phase 1 used `./mvnw`) | — | — |
| PostgreSQL 16 | Testcontainers | postgres:16-alpine (existing) | 16-alpine | — |

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Spring Boot Test + Testcontainers + Mockito 5.14.2 |
| Config file | `pom.xml` (maven-surefire-plugin 3.5.2, jacoco 0.8.12) |
| Quick run command | `cd backend/java && ./mvnw test -Dtest=W1IndicatorPipelineIntegrationTest -q` |
| Full suite command | `cd backend/java && ./mvnw test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| INDC-01 | SuperTrend rows exist in `indicator_supertrend` for timeframe='W1' after processing | integration | `./mvnw test -Dtest=W1IndicatorPipelineIntegrationTest#supertrend_W1_isComputed -q` | ❌ Wave 0 |
| INDC-02 | RSI rows exist in `indicator_rsi` for timeframe='W1' after processing | integration | `./mvnw test -Dtest=W1IndicatorPipelineIntegrationTest#rsi_W1_isComputed -q` | ❌ Wave 0 |
| INDC-03 | Re-running indicator computation on W1 does not increase row count (idempotent) | integration | `./mvnw test -Dtest=W1IndicatorPipelineIntegrationTest#indicators_W1_areIdempotent -q` | ❌ Wave 0 |
| SGNL-01 | `signal_state` rows exist for timeframe='W1' with BULLISH/BEARISH/UNKNOWN trend_state | integration | `./mvnw test -Dtest=W1IndicatorPipelineIntegrationTest#signalState_W1_isDetected -q` | ❌ Wave 0 |
| SGNL-02 | `market_breadth_snapshot` row exists for timeframe='W1' with non-zero counts | integration | `./mvnw test -Dtest=W1IndicatorPipelineIntegrationTest#marketBreadth_W1_isComputed -q` | ❌ Wave 0 |

**Test fixture requirements:**
- Minimum 15 W1 candles (to satisfy RSI-14 minimum: period + 1)
- Candle `close_time` must be past dates (before UTC midnight today) to pass `CandleFilters`
- Use weeks starting 2024-01-07 (Sunday close) through 2024-04-07 for stable fixture dates
- `source = MarketProvider.BINANCE` (required, not nullable)

### Sampling Rate

- **Per task commit:** `cd backend/java && ./mvnw test -Dtest=W1IndicatorPipelineIntegrationTest -q`
- **Per wave merge:** `cd backend/java && ./mvnw test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/walshe/projectcolumbo/persistence/service/W1IndicatorPipelineIntegrationTest.java` — covers INDC-01, INDC-02, INDC-03, SGNL-01, SGNL-02 (single file, 5 test methods)

*(A single integration test class covers all five requirements end-to-end with a shared W1 candle fixture, following the `CandleRollupIntegrationTest` + `MarketPipelineIntegrationTest` precedent.)*

---

## Security Domain

This phase is data-pipeline-internal with no new HTTP endpoints, user input, or authentication
changes. The applicable ASVS categories are identical to Phase 1:

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | — |
| V3 Session Management | No | — |
| V4 Access Control | No | — |
| V5 Input Validation | Minimal | All inputs derive from DB timestamps and enum constants, not user input |
| V6 Cryptography | No | — |

No new security surface is introduced in Phase 2.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `detectDaily()` is the correct W1 signal entry point (it iterates `Timeframe.values()`) rather than a new dedicated W1 method | Architecture Patterns Pattern 3, Pitfall 1 | If someone adds a D1-only guard inside `detectDaily()` before Phase 2 is implemented, W1 signals won't be produced |
| A2 | MarketPulseService upsert stub (no-op on revision) is acceptable for Phase 2 | Common Pitfalls Pitfall 2, Open Questions | If revision case occurs (unlikely for first-run W1 breadth), the snapshot silently stays stale |
| A3 | W1 ATR warmup with fewer than 100 weeks of history produces correct (if slightly less stable) SuperTrend values | Common Pitfalls Pitfall 3 | The first N SuperTrend values may differ slightly from a full-history recalculation, but the direction signal is still reliable |
| A4 | RSI "full recalculation but persist only new" approach in `RsiComputationService` is acceptable for W1 (perf not a concern at weekly granularity) | Detailed Service Analysis | For high asset counts with years of W1 history, this adds compute time — irrelevant for current scale |

**If this table is empty:** N/A — the table above has 4 assumptions. All critical paths are verified in the codebase.

---

## Sources

### Primary (HIGH confidence — codebase verified)

- `SuperTrendService.java` — `processAllActiveAssets(Timeframe, int, BigDecimal, boolean)` signature and body [VERIFIED]
- `RsiComputationService.java` — `computeForActiveAssets(Timeframe, int, boolean)` signature and body [VERIFIED]
- `SignalStateService.java` — `detectDaily()` iterates `Timeframe.values()`, `processAsset(Asset, Timeframe, boolean)` [VERIFIED]
- `MarketPulseService.java` — `computeDaily()` hardcodes D1, `computePulseForIndicator(Timeframe, IndicatorType)` is parameterized [VERIFIED]
- `SuperTrendCalculator.java` — `calculateIncremental` with `atrLength * 10` warmup window [VERIFIED]
- `RsiCalculator.java` — `size() <= period` minimum candle guard [VERIFIED]
- `SignalStateCalculator.java` — `calculate(indicators, previousDirection)`, `calculateRsi(indicators, previousTrend)` [VERIFIED]
- `SuperTrendRepository.java`, `RsiRepository.java`, `SignalStateRepository.java`, `MarketBreadthSnapshotRepository.java` — all methods accept `Timeframe` [VERIFIED]
- `Timeframe.java` — confirms `W1("1W")` is present after Phase 1 [VERIFIED]
- `V2, V3, V4, V9` migrations — schema confirms `timeframe` column uses the `timeframe` enum in all indicator/signal/breadth tables [VERIFIED]
- `SuperTrendServiceTest.java`, `MarketPipelineIntegrationTest.java`, `CandleRollupIntegrationTest.java` — test patterns to mirror [VERIFIED]

### Secondary (MEDIUM confidence)

- None required — all critical decisions are grounded in the codebase directly.

### Tertiary (LOW confidence)

- None.

---

## Metadata

**Confidence breakdown:**
- Service parameterization analysis: HIGH — read every service class and repository in scope
- Architecture (what changes, what doesn't): HIGH — all service signatures confirmed
- Pitfalls: HIGH — derived from reading actual code paths, not assumptions
- Validation Architecture: HIGH — mirrors existing test patterns directly

**Research date:** 2026-05-21
**Valid until:** 2026-06-21 (stable domain — Spring Boot 4.0.2, no fast-moving dependencies)
