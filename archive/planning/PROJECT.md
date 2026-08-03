# Project Colombo

## What This Is

A crypto market analysis backend built in Spring Boot that ingests OHLCV candles from Binance, computes technical indicators (SuperTrend, RSI, EMA, MACD, Elder Impulse, Market Thermometer), detects trend signal state changes, and aggregates market breadth snapshots. It exposes a REST API for querying signals, market pulse, and running multi-condition asset scans — including cross-timeframe scans that filter across D1 and W1 in a single query.

## Core Value

Give traders a clear, up-to-date view of trend signals across a crypto asset universe — across multiple timeframes — so they can make faster, better-informed decisions. Specifically: surface the Elder Impulse System's GREEN/RED/NEUTRAL permission states and the Market Thermometer's entry-timing signal to reduce bad trades and improve entry quality.

## Requirements

### Validated

- ✓ Daily candle ingestion from Binance with incremental gap-bridging — existing
- ✓ SuperTrend indicator (ATR 10, multiplier 2.0) computed on D1 — existing
- ✓ RSI indicator (period 14, Wilder's smoothing) computed on D1 — existing
- ✓ Signal state detection (SUPERTREND_BULLISH/BEARISH/UNKNOWN + flip events) on D1 — existing
- ✓ Market breadth snapshots aggregated daily (% bullish, % bearish, % unknown) — existing
- ✓ REST API: supertrend-market-pulse, signal query, multi-condition scan, summary report — existing
- ✓ Pipeline run tracking (RUNNING/SUCCESS/PARTIAL/FAILED) with concurrency guard — existing
- ✓ Flyway-managed PostgreSQL schema — existing
- ✓ Weekly (W1) candles derived by rolling up daily (D1) candles — Phase 1, v1.0
- ✓ SuperTrend and RSI computed on W1 candles — Phase 2, v1.0
- ✓ Signal state detection and market breadth on W1 — Phase 2, v1.0
- ✓ All query/scan APIs support W1 timeframe — Phase 3, v1.0
- ✓ Scan API supports conditions across different timeframes in a single request — Phase 4, v2.0
- ✓ Each scan condition carries its own timeframe — Phase 4, v2.0
- ✓ Scan response includes timeframe per matched indicator — Phase 4, v2.0
- ✓ TradingView URL uses highest-granularity timeframe present — Phase 4, v2.0

### Active (v3.0)

- [ ] EMA computed on D1 (period 13) and W1 (period 26) — foundation for Impulse
- [ ] MACD (12-26-9) histogram computed on D1 — foundation for Impulse
- [ ] Elder Impulse state (GREEN/RED/NEUTRAL) derived from 13-EMA slope + MACD-H slope on D1
- [ ] W1 strategic direction derived from 26-EMA slope (UP/DOWN/FLAT) — Impulse Screen 1 filter
- [ ] Elder Impulse state stored in signal_state; scan/signal APIs support ELDER_IMPULSE indicator type
- [ ] `GET /api/v1/elder-impulse-market-pulse` endpoint returns market-wide breadth of GREEN/RED/NEUTRAL
- [ ] Market Thermometer raw values (temperature + 22-day EMA) stored per asset per day
- [ ] Market Thermometer state (QUIET/HOT/SPIKE) derived and stored in signal_state
- [ ] Scan API supports MARKET_THERMOMETER indicator type with state filters
- [ ] API exposes thermometer raw numeric values (temp + EMA) alongside state

### Out of Scope

- Fetching weekly candles directly from Binance — rolled-up from daily for internal consistency
- 4hr (H4) base timeframe — planned for later
- Frontend/UI — API only
- Real-time streaming — daily scheduled pipeline only
- Screen 3 (intraday entry) — belongs to TradingView MCP, not Columbo
- Triple Screen Force Index — out of scope for this milestone
- Alert/notification delivery — API-only for v3.0

## Context

- Built previously with Junie/Claude/OpenSpec, not originally using GSD
- v1.0 milestone complete (2026-05-22): D1+W1 full pipeline, indicators, signals, breadth, all APIs
- v2.0 milestone complete (2026-05-24): Cross-timeframe scan; per-condition timeframe; MatchedIndicator.timeframe
- v3.0 focus: Elder Impulse System + Market Thermometer — two Elder trading systems implemented as new indicator pipelines
- Elder Impulse: Screen 1 = weekly 26-EMA slope (strategic direction), Screen 2 = daily 13-EMA + MACD-H slopes (GREEN/RED/NEUTRAL permission)
- Market Thermometer: daily volatility heat gauge — QUIET/HOT/SPIKE states guide entry timing
- Primary daily trading use: scan for assets where W1 IMPULSE_GREEN AND D1 IMPULSE_GREEN AND D1 THERMOMETER_QUIET
- Codebase map: `.planning/codebase/`

## Constraints

- **Tech stack**: Java 17, Spring Boot 4.0.2, PostgreSQL, Flyway — no changes to these
- **Backward compatibility**: All existing API requests (v1.0, v2.0) continue to work unchanged
- **API surface**: REST API only — no webhooks, no UI, no streaming
- **Enum naming convention**: All enum values prefixed with indicator type (established in refactor/enum-names)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Derive W1 from D1 rollup (not fetch from Binance) | Internal consistency; sets up cleanly for 4hr → D1 → W1 derivation chain later | Implemented — Phase 1, v1.0 |
| Rollup mechanism must be timeframe-generic | When H4 is added, same code should derive D1 and W1 without special-casing | Implemented — Phase 1, v1.0 |
| `W1IndicatorService` as thin orchestrator (no calculator logic) | Single clean call site for Phase 3 wiring; keeps services independently testable | Implemented — Phase 2, v1.0 |
| `detectDaily()` covers W1 (no new detectW1()) | `detectDaily()` already iterates `Timeframe.values()` — W1 included automatically after Phase 1 adds it to the enum | Implemented — Phase 2, v1.0 |
| `timeframe` moves to `ScanCondition`, top-level becomes optional fallback | Enables cross-timeframe AND scans; backward-compatible | v2.0 — Phase 4 |
| `MatchedIndicator` subtypes gain `timeframe` field | Caller needs to know which timeframe each matched indicator came from | v2.0 — Phase 4 |
| EMA and MACD stored as raw indicator tables, not derived inline | Reusable for future indicators; queryable independently; mirrors RSI/SuperTrend pattern | v3.0 decision |
| Elder Impulse state stored in signal_state (not a separate table) | The existing state machine with TrendState + SignalEvent + flip detection handles it perfectly; scan/signal APIs work for free | v3.0 decision |
| Market Thermometer: raw values in indicator table + categorical state in signal_state | Numeric values (temp, EMA) needed for profit target calculations; categorical state drives scan filters | v3.0 decision |
| New enum values prefixed with indicator type | Consistent with SUPERTREND_BULLISH / RSI_ABOVE_60 convention established in refactor | v3.0 decision |

---
*Last updated: 2026-05-29 — v3.0 milestone initialized*

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
