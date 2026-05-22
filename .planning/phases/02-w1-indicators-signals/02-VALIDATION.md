# Phase 2: W1 Indicators & Signals - Validation Architecture

**Extracted:** 2026-05-21 (from 02-RESEARCH.md "Validation Architecture")
**Phase:** 02-w1-indicators-signals
**Nyquist validation:** enabled (`config.json` workflow.nyquist_validation = true)

---

## Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Spring Boot Test + Testcontainers + Mockito 5.14.2 |
| Config file | `backend/java/pom.xml` (maven-surefire-plugin 3.5.2, jacoco 0.8.12) |
| Database under test | PostgreSQL 16 (`postgres:16-alpine`) via Testcontainers |
| Quick run command | `cd backend/java && ./mvnw test -Dtest=W1IndicatorPipelineIntegrationTest -q` |
| Full suite command | `cd backend/java && ./mvnw test` |

---

## Phase Validation Contract — What "Green" Means

Phase 2 is validated when **all of the following hold simultaneously**:

1. `W1IndicatorPipelineIntegrationTest` exists and all **6** of its test methods pass against a Testcontainers Postgres instance:
   - 5 per-requirement methods exercising the individual services directly (one per requirement ID below).
   - 1 orchestrator method (`orchestrator_processAllActiveAssets_producesAllOutputs`) exercising `W1IndicatorService.processAllActiveAssets()` end-to-end.
2. `MarketPulseServiceTest` passes unchanged — proving the `computeDaily()` -> `computeForTimeframe(Timeframe.D1)` refactor preserved D1 behaviour.
3. The full Maven suite (`cd backend/java && ./mvnw test`) reports **BUILD SUCCESS** with zero failures and zero errors — no existing D1 indicator, signal, pulse, pipeline, or rollup test regressed.

"Green" is binary: any failing or skipped test in the surefire summary means Phase 2 is NOT validated. Test assertions must not be weakened or disabled to reach green — a regression is fixed at the source.

Every requirement ID in the map below MUST have a corresponding passing test method. The orchestrator behaviour (a single `W1IndicatorService` sequences SuperTrend -> RSI -> signals -> breadth in strict order) is itself a validated must-have, covered by `orchestrator_processAllActiveAssets_producesAllOutputs` — not assumed from the per-requirement tests.

---

## Phase Requirements — Test Name Map

| Req ID | Behavior | Test Type | Test Method | Automated Command | Plan |
|--------|----------|-----------|-------------|-------------------|------|
| INDC-01 | SuperTrend rows exist in `indicator_supertrend` for timeframe='W1' after processing | integration | `W1IndicatorPipelineIntegrationTest#supertrend_W1_isComputed` | `cd backend/java && ./mvnw test -Dtest=W1IndicatorPipelineIntegrationTest#supertrend_W1_isComputed -q` | 02-02 |
| INDC-02 | RSI rows exist in `indicator_rsi` for timeframe='W1' after processing | integration | `W1IndicatorPipelineIntegrationTest#rsi_W1_isComputed` | `cd backend/java && ./mvnw test -Dtest=W1IndicatorPipelineIntegrationTest#rsi_W1_isComputed -q` | 02-02 |
| INDC-03 | Re-running indicator computation on W1 does not increase row count (idempotent) | integration | `W1IndicatorPipelineIntegrationTest#indicators_W1_areIdempotent` | `cd backend/java && ./mvnw test -Dtest=W1IndicatorPipelineIntegrationTest#indicators_W1_areIdempotent -q` | 02-02 |
| SGNL-01 | `signal_state` rows exist for timeframe='W1' with BULLISH/BEARISH/UNKNOWN trend_state | integration | `W1IndicatorPipelineIntegrationTest#signalState_W1_isDetected` | `cd backend/java && ./mvnw test -Dtest=W1IndicatorPipelineIntegrationTest#signalState_W1_isDetected -q` | 02-02 |
| SGNL-02 | `market_breadth_snapshot` row exists for timeframe='W1' with non-zero counts | integration | `W1IndicatorPipelineIntegrationTest#marketBreadth_W1_isComputed` | `cd backend/java && ./mvnw test -Dtest=W1IndicatorPipelineIntegrationTest#marketBreadth_W1_isComputed -q` | 02-02 |

### Orchestrator coverage (must-have, not a numbered requirement)

| Must-have | Behavior | Test Type | Test Method | Automated Command | Plan |
|-----------|----------|-----------|-------------|-------------------|------|
| W1 orchestration | A single `W1IndicatorService.processAllActiveAssets()` call sequences SuperTrend W1 -> RSI W1 -> signal detection -> W1 breadth in strict order; after it, all four W1 output tables (SuperTrend, RSI, signal state, market breadth) are populated | integration | `W1IndicatorPipelineIntegrationTest#orchestrator_processAllActiveAssets_producesAllOutputs` | `cd backend/java && ./mvnw test -Dtest=W1IndicatorPipelineIntegrationTest#orchestrator_processAllActiveAssets_producesAllOutputs -q` | 02-02 |

This test makes exactly one call — `w1IndicatorService.processAllActiveAssets()` — and asserts all four output tables are populated. Because signal detection requires prior indicators (RESEARCH Pitfall 4) and breadth requires prior signals (RESEARCH Pitfall 2), this single assertion set proves the orchestrator's strict step ordering and completeness. The per-requirement tests above call the underlying services directly and do not exercise the orchestrator.

---

## Test Fixture Requirements

- Minimum **15** W1 candles per seeded asset (RSI-14 minimum = period + 1 = 15; SuperTrend-10 minimum = 10).
- Candle `close_time` must be past dates (before UTC midnight today) so `CandleFilters.finalizedBeforeUtcMidnightToday` passes them through.
- Use Sunday close times starting `2024-01-07 23:59:59.999 UTC`, one week apart (`2024-01-07` through ~`2024-04-07` for a 15-candle fixture).
- `openTime` = `closeTime` minus 6 days at `00:00:00`.
- OHLCV literals: open 40000, high 41000, low 39000, close 40500, volume 1000.
- `source = MarketProvider.BINANCE` (required column, not nullable).
- Fixture helper: `seedAssetWithW1Candles(String symbol, int count)` — see 02-PATTERNS.md for the exact body.

---

## Sampling Rate

- **Per task commit:** `cd backend/java && ./mvnw test -Dtest=W1IndicatorPipelineIntegrationTest -q`
- **Per wave merge:** `cd backend/java && ./mvnw test`
- **Phase gate (before `/gsd:verify-work`):** `cd backend/java && ./mvnw test` — full suite green

---

## Wave 0 Gaps

- [ ] `backend/java/src/test/java/walshe/projectcolumbo/persistence/service/W1IndicatorPipelineIntegrationTest.java` — covers INDC-01, INDC-02, INDC-03, SGNL-01, SGNL-02 (5 per-requirement test methods) plus `orchestrator_processAllActiveAssets_producesAllOutputs` (orchestrator coverage). Single file, 6 test methods total.

A single integration test class covers all five requirements end-to-end plus the orchestrator, with a shared W1 candle fixture, following the `CandleRollupIntegrationTest` + `MarketPipelineIntegrationTest` precedent. Created in Plan 02-02 Task 1; runs green after Plan 02-02 Task 2 implements `W1IndicatorService`.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker | Testcontainers integration tests | Assumed yes (Phase 1 tests passed) | — | — |
| Maven wrapper (mvnw) | Build + test | Confirmed (Phase 1 used `./mvnw`) | — | — |
| PostgreSQL 16 | Testcontainers | postgres:16-alpine (existing) | 16-alpine | — |

No new external dependencies are introduced in Phase 2 — all test tooling is already in `pom.xml`.
</content>
