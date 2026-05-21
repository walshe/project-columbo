# Project Colombo

## What This Is

A crypto market analysis backend built in Spring Boot that ingests OHLCV candles from Binance, computes technical indicators (SuperTrend, RSI), detects trend signal state changes, and aggregates market breadth snapshots. It exposes a REST API for querying signals, market pulse, and running multi-condition asset scans.

## Core Value

Give traders a clear, up-to-date view of trend signals across a crypto asset universe — across multiple timeframes — so they can make faster, better-informed decisions.

## Requirements

### Validated

- ✓ Daily candle ingestion from Binance with incremental gap-bridging — existing
- ✓ SuperTrend indicator (ATR 10, multiplier 2.0) computed on D1 — existing
- ✓ RSI indicator (period 14, Wilder's smoothing) computed on D1 — existing
- ✓ Signal state detection (BULLISH/BEARISH/UNKNOWN + BULLISH_CROSS/BEARISH_CROSS events) on D1 — existing
- ✓ Market breadth snapshots aggregated daily (% bullish, % bearish, % unknown) — existing
- ✓ REST API: market pulse, signal query, multi-condition scan, summary report — existing
- ✓ Pipeline run tracking (RUNNING/SUCCESS/PARTIAL/FAILED) with concurrency guard — existing
- ✓ Flyway-managed PostgreSQL schema — existing

### Active

- [ ] Weekly (W1) candles derived by rolling up daily (D1) candles — Validated in Phase 1
- [ ] SuperTrend and RSI computed on W1 candles — Validated in Phase 2
- [ ] Signal state detection and market breadth on W1 — Validated in Phase 2
- [ ] All query/scan APIs support W1 timeframe

### Out of Scope

- Fetching weekly candles directly from Binance — rolled-up from daily for internal consistency; avoids week-boundary precision mismatch
- 4hr (H4) base timeframe — planned for later; rollup mechanism designed to support it when added
- Frontend/UI — API only for now
- Real-time streaming — daily scheduled pipeline only

## Context

- Built previously with Junie/Claude/OpenSpec, not originally using GSD
- `Timeframe` enum now has `D1` and `W1`; pipeline still hardcodes `D1` throughout `MarketPipelineService` — Phase 3 wires W1 in
- W1 candles derived via `CandleRollupService` (Phase 1 complete); W1 indicators/signals/breadth computable via `W1IndicatorService` (Phase 2 complete)
- Future direction: 4hr may become the base timeframe, with D1 and W1 derived from it via the same rollup mechanism
- Codebase map: `.planning/codebase/`

## Constraints

- **Tech stack**: Java 17, Spring Boot 4.0.2, PostgreSQL, Flyway — no changes to these
- **Architecture**: Rollup must fit the existing 4-phase pipeline (INGESTION → INDICATOR → SIGNAL → MARKET_PULSE)
- **Data integrity**: Week boundaries must be consistent (Monday open → Sunday close UTC); partial weeks must not produce candles
- **Backward compatibility**: D1 pipeline must continue to work unchanged

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Derive W1 from D1 rollup (not fetch from Binance) | Internal consistency; sets up cleanly for 4hr → D1 → W1 derivation chain later | Implemented — Phase 1 |
| Rollup mechanism must be timeframe-generic | When H4 is added, same code should derive D1 and W1 without special-casing | Implemented — Phase 1 |
| `W1IndicatorService` as thin orchestrator (no calculator logic) | Single clean call site for Phase 3 wiring; keeps services independently testable | Implemented — Phase 2 |
| `detectDaily()` covers W1 (no new detectW1()) | `detectDaily()` already iterates `Timeframe.values()` — W1 included automatically after Phase 1 adds it to the enum | Implemented — Phase 2 |

---
*Last updated: 2026-05-21 after Phase 2 completion*

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition:**
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone:**
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state
