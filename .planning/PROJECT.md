# Project Colombo

## What This Is

A crypto market analysis backend built in Spring Boot that ingests OHLCV candles from Binance, computes technical indicators (SuperTrend, RSI), detects trend signal state changes, and aggregates market breadth snapshots. It exposes a REST API for querying signals, market pulse, and running multi-condition asset scans — including cross-timeframe scans that filter across D1 and W1 in a single query.

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
- ✓ Weekly (W1) candles derived by rolling up daily (D1) candles — Phase 1, v1.0
- ✓ SuperTrend and RSI computed on W1 candles — Phase 2, v1.0
- ✓ Signal state detection and market breadth on W1 — Phase 2, v1.0
- ✓ All query/scan APIs support W1 timeframe — Phase 3, v1.0

### Active (v2.0)

- [ ] Scan API supports conditions across different timeframes in a single request (multi-timeframe AND scan)
- [ ] Each scan condition carries its own timeframe; top-level timeframe is optional (backward-compatible fallback)
- [ ] Scan response includes timeframe per matched indicator so the caller can see which timeframe each match came from
- [ ] TradingView URL in scan results uses the highest-granularity timeframe present in the request

### Out of Scope

- Fetching weekly candles directly from Binance — rolled-up from daily for internal consistency; avoids week-boundary precision mismatch
- 4hr (H4) base timeframe — planned for later; rollup mechanism designed to support it when added
- Frontend/UI — API only for now
- Real-time streaming — daily scheduled pipeline only
- Summary endpoint multi-timeframe support — excluded from v2.0 scope

## Context

- Built previously with Junie/Claude/OpenSpec, not originally using GSD
- v1.0 milestone complete (2026-05-22): D1+W1 full pipeline, indicators, signals, breadth, all APIs
- v2.0 focus: cross-timeframe scan — `ScanCondition` gains a per-condition `timeframe`; `ScanRequest.timeframe` becomes optional as a fallback default; `MatchedIndicator` subtypes gain `timeframe` field
- Current scan API: single `timeframe` on `ScanRequest` applies to all conditions — v2 removes this constraint
- Strategies reference: `strategies/bullmania_daily_supertrend.pine` / `.mq5` — Bullmania Multi-Timeframe SuperTrend strategy uses exactly the kind of D1+W1 confluence this API change enables
- Codebase map: `.planning/codebase/`

## Constraints

- **Tech stack**: Java 17, Spring Boot 4.0.2, PostgreSQL, Flyway — no changes to these
- **Backward compatibility**: Existing single-timeframe scan requests must continue to work unchanged
- **API surface**: REST API only — no webhooks, no UI, no streaming

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Derive W1 from D1 rollup (not fetch from Binance) | Internal consistency; sets up cleanly for 4hr → D1 → W1 derivation chain later | Implemented — Phase 1, v1.0 |
| Rollup mechanism must be timeframe-generic | When H4 is added, same code should derive D1 and W1 without special-casing | Implemented — Phase 1, v1.0 |
| `W1IndicatorService` as thin orchestrator (no calculator logic) | Single clean call site for Phase 3 wiring; keeps services independently testable | Implemented — Phase 2, v1.0 |
| `detectDaily()` covers W1 (no new detectW1()) | `detectDaily()` already iterates `Timeframe.values()` — W1 included automatically after Phase 1 adds it to the enum | Implemented — Phase 2, v1.0 |
| `timeframe` moves to `ScanCondition`, top-level becomes optional fallback | Enables cross-timeframe AND scans; backward-compatible (existing callers still work if they pass top-level timeframe) | v2.0 — Phase 1 |
| `MatchedIndicator` subtypes gain `timeframe` field | Caller needs to know which timeframe each matched indicator came from to act on results | v2.0 — Phase 1 |

---
*Last updated: 2026-05-24 — v2.0 milestone initialized*

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
