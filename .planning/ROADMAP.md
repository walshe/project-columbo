# Roadmap: Project Colombo — Weekly Timeframe

**Generated:** 2026-05-20
**Phases:** 3
**Requirements:** 16 v1 requirements, 100% mapped ✓

---

## Phase Overview

| # | Phase | Goal | Requirements | Success Criteria |
|---|-------|------|--------------|-----------------|
| 1 | W1 Candle Derivation | 3/3 | Complete    | 2026-05-21 |
| 2 | W1 Indicators & Signals | 2/2 | Complete   | 2026-05-21 |
| 3 | Pipeline & API Integration | Wire W1 into the daily pipeline and expose via all query endpoints | PIPE-01–03, API-01–03 | 5 |

---

## Phase 1: W1 Candle Derivation

**Goal:** Implement a generic timeframe rollup service that derives complete W1 candles from D1 candles and persists them in the existing `candle` table.

**Requirements:** CNDL-01, CNDL-02, CNDL-03, CNDL-04, CNDL-05

**Plans:** 3/3 plans complete

Plans:
**Wave 1**

- [x] 01-01-PLAN.md — Add W1 to the PostgreSQL `timeframe` enum (Flyway V13) and the Java `Timeframe` enum

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 01-02-PLAN.md — Implement the generic `CandleRollupService` (week grouping, completeness guard, idempotent aggregation) with unit tests

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 01-03-PLAN.md — Testcontainers integration tests for DB-enum validity, idempotency, and incremental rollup; full-suite phase gate

**Success Criteria:**

1. `W1` exists as a valid `Timeframe` enum value and in the DB enum (Flyway migration applied)
2. Given a set of D1 candles for a full week (Mon–Sun UTC), the rollup service produces one W1 candle with correct O/H/L/C/V (open = Mon open, high = max daily high, low = min daily low, close = Sun close, volume = sum)
3. Incomplete weeks (current week not yet closed) produce no W1 candle
4. Running rollup twice for the same week produces no duplicate (idempotent upsert)
5. The rollup component is parameterized by source and target `Timeframe` — no D1/W1 literals in the core logic

---

## Phase 2: W1 Indicators & Signals

**Goal:** Compute SuperTrend and RSI on W1 candles for all active assets, detect weekly signal states, and produce weekly market breadth snapshots.

**Requirements:** INDC-01, INDC-02, INDC-03, SGNL-01, SGNL-02

**Plans:** 2/2 plans complete

Plans:
**Wave 1**

- [x] 02-01-PLAN.md — Refactor `MarketPulseService` — extract timeframe-parameterized `computeForTimeframe(Timeframe)` from D1-hardcoded `computeDaily()`

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 02-02-PLAN.md — `W1IndicatorService` orchestrator (SuperTrend → RSI → signals → breadth on W1) plus end-to-end integration tests for all five requirements

**Success Criteria:**

1. After rollup, `SuperTrendIndicator` and `RsiIndicator` rows exist for W1 candles for all active assets
2. Incremental computation: re-running the W1 indicator pass does not recompute already-stored W1 indicators
3. `SignalState` rows (BULLISH/BEARISH/UNKNOWN + cross events) exist for W1 per asset
4. `MarketBreadthSnapshot` exists for W1 timeframe reflecting the current week's signal distribution

---

## Phase 3: Pipeline & API Integration

**Goal:** Integrate W1 derivation, indicator computation, and signal detection into the daily pipeline run, and expose W1 data through all existing query and scan endpoints.

**Requirements:** PIPE-01, PIPE-02, PIPE-03, API-01, API-02, API-03

**Plans:** 2 plans

Plans:
**Wave 1**

- [ ] 03-01-PLAN.md — Wire W1 rollup + W1 indicator/signal/breadth pass into `MarketPipelineService.runDaily()` as additive phases (PIPE-01/02/03)
- [ ] 03-02-PLAN.md — W1 API integration tests proving market-pulse, signals, and scan endpoints serve W1 data (API-01/02/03)

**Success Criteria:**

1. A scheduled or manually triggered pipeline run produces W1 candles, indicators, signals, and market breadth without any manual intervention
2. D1 pipeline behaviour is unchanged — W1 is a new additive phase, not a replacement
3. `GET /api/v1/market-pulse?timeframe=W1` returns a valid market breadth response
4. `GET /api/v1/signals?timeframe=W1` returns W1 signal states
5. `POST /api/v1/scan` with `"timeframe": "W1"` in conditions returns correct W1 scan results

---

## Design Notes

### Rollup Architecture

The rollup should be a standalone `CandleRollupService` (or similar) that accepts:

- `sourceTimeframe` — the input candle granularity (e.g. `D1`)
- `targetTimeframe` — the output candle granularity (e.g. `W1`)
- `weekStartDay` — configurable (Monday UTC)

This keeps H4→D1 and H4→W1 as future callers of the same service without refactoring.

### Pipeline Ordering

After Phase 3, the daily pipeline phases become:

```
Phase 1: INGESTION        (D1 from Binance — unchanged)
Phase 2: D1 INDICATORS    (SuperTrend, RSI on D1 — unchanged)
Phase 3: D1 SIGNALS       (signal detection on D1 — unchanged)
Phase 4: D1 MARKET_PULSE  (market breadth on D1 — unchanged)
Phase 5: W1 ROLLUP        (derive W1 from D1)
Phase 6: W1 INDICATORS    (SuperTrend, RSI on W1)
Phase 7: W1 SIGNALS       (signal detection on W1)
Phase 8: W1 MARKET_PULSE  (market breadth on W1)
```

### Week Boundary Convention

- Week: Monday 00:00:00 UTC → Sunday 23:59:59 UTC
- A W1 candle is only created when all 7 daily candles for a calendar week exist
- Current (incomplete) week is never rolled up

---
*Roadmap created: 2026-05-20*
*Phase 1 planned: 2026-05-20 — 3 plans*
*Phase 2 planned: 2026-05-21 — 2 plans*
*Phase 3 planned: 2026-05-21 — 2 plans*
