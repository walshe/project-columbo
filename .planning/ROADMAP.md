# Roadmap: Project Colombo — v3.0 Elder Impulse + Market Thermometer

**Generated:** 2026-05-29
**Phases:** 3 (phases 5–7)
**Requirements:** 24 v3.0 requirements, 100% mapped ✓

> Phase numbering continues from v2.0 (phases 1–4 complete). Phase 5 = first phase of v3.0.

---

## Phase Overview

| # | Phase | Goal | Requirements | Plans |
|---|-------|------|--------------|-------|
| 5 | EMA + MACD Foundation | Store raw EMA and MACD indicator values for D1 and W1 | EMA-01–04, MACD-01–03 | ~3 |
| 6 | Elder Impulse State | Derive GREEN/RED/NEUTRAL states from EMA + MACD slopes; new endpoint + scan/signal support | IMPL-01–08 | ~3 |
| 7 | Market Thermometer | Compute temperature indicator; derive QUIET/HOT/SPIKE states; scan/signal support | THERM-01–09 | ~3 |

---

## Phase 5: EMA + MACD Foundation

**Goal:** Lay the indicator data foundation for the Elder Impulse System. Compute and store EMA values (13-period D1, 26-period W1) and MACD 12-26-9 on D1. No new API surface — purely internal pipeline additions. Phase 6 will read from these tables to derive impulse states.

**Requirements:** EMA-01 through EMA-04, MACD-01 through MACD-03

**Design decisions:**
- `indicator_ema` mirrors `indicator_rsi` schema (asset FK + timeframe + period + close_time + value, unique on the 4-tuple)
- `indicator_macd` stores three columns: `macd_line`, `signal_line`, `histogram` — all required for Phase 6 slope detection
- EMA computation uses standard formula: EMA_n = close_n × k + EMA_(n-1) × (1 − k), where k = 2/(period+1). Seed = SMA of first N periods
- Both EMA and MACD computed incrementally — read last stored value, compute forward from the latest stored close_time
- 26-period EMA on W1 requires at least 26 completed weekly candles per asset before first value is emitted

**Plans:** ~3 sequential

### Wave 1 — Schema + Calculators

- [x] **05-01-PLAN.md** — Flyway migration V15: create `indicator_ema` and `indicator_macd` tables. `EmaCalculator`: two overloads (candle-based + value-based for MACD signal line reuse). `MacdCalculator`: MACD 12-26-9 using `EmaCalculator` internally for fast/slow/signal EMAs.

### Wave 2 — Pipeline Integration

- [x] **05-02-PLAN.md** — `EmaIndicator` entity + `EmaRepository` (period-scoped queries). `EmaComputationService`: incremental EMA for any period/timeframe. `MacdIndicator` entity + `MacdRepository`. `MacdComputationService`: incremental MACD for D1. Wire into `MarketPipelineService` (D1 EMA-13 + MACD) and `W1IndicatorService` (W1 EMA-26 only — no MACD for W1).

### Wave 3 — Tests

- [x] **05-03-PLAN.md** — `EmaCalculatorTest`: seed=SMA, EMA progression with k=0.5 (period=3), both overloads agree. `MacdCalculatorTest`: boundary guards, histogram identity. `EmaComputationIntegrationTest`: seed 50 D1 candles, assert indicator_ema rows (38 for p=13, 17 MACD rows), idempotency, period isolation.

**Success Criteria:**
1. After pipeline run, `indicator_ema` contains D1 13-period and W1 26-period rows for each active asset
2. After pipeline run, `indicator_macd` contains D1 histogram rows for each active asset
3. EMA and MACD computation is incremental — re-running does not duplicate rows
4. All existing tests pass

---

## Phase 6: Elder Impulse State

**Goal:** Derive the Elder Impulse GREEN/RED/NEUTRAL permission state from the EMA and MACD data built in Phase 5. Store in the existing `signal_state` table. Expose via existing signal query and scan APIs. Add the `elder-impulse-market-pulse` endpoint.

**Requirements:** IMPL-01 through IMPL-08

**Design decisions:**
- D1 impulse: `IMPULSE_GREEN` when 13-EMA slope > 0 AND MACD-H slope > 0; `IMPULSE_RED` when both < 0; `IMPULSE_NEUTRAL` otherwise
- W1 impulse: `IMPULSE_GREEN` when 26-EMA slope > 0; `IMPULSE_RED` when slope < 0; `IMPULSE_NEUTRAL` when effectively flat (|slope| below a configurable threshold — default epsilon based on asset price scale, or simply use slope == 0 comparison to avoid floating-point issues in first iteration)
- Slope = current period's indicator value minus previous period's value
- Flip detection: existing `SignalDetectionService.detectDaily()` iterates `Timeframe.values()` — ELDER_IMPULSE is included automatically when added to `IndicatorType`
- Market breadth for Elder Impulse aggregated by existing `MarketBreadthService` once `IndicatorType.ELDER_IMPULSE` is added
- `ElderImpulseMatch` sealed subtype added to `MatchedIndicator`; `ScanResult` Swagger discriminator updated to include it

**Plans:** ~3 sequential

### Wave 1 — Enums + DB Migration

- [x] **06-01-PLAN.md** — Flyway migration: add `ELDER_IMPULSE` to `indicator_type` DB enum; add `IMPULSE_GREEN`, `IMPULSE_RED`, `IMPULSE_NEUTRAL` to `trend_state`; add `IMPULSE_TURNED_GREEN`, `IMPULSE_TURNED_RED`, `IMPULSE_TURNED_NEUTRAL` to `signal_event`. Update Java enums (`IndicatorType`, `TrendState`, `SignalEvent`) to match. Update `MatchedIndicator`/`ScanResult` Swagger discriminator to include `ElderImpulseMatch`.

### Wave 2 — State Derivation + Pipeline

- [x] **06-02-PLAN.md** — `ElderImpulseStateService`: reads latest two EMA rows (D1 13-period) and latest two MACD histogram rows for each asset; derives GREEN/RED/NEUTRAL state; persists into `signal_state`. `W1ImpulseStateService`: reads latest two W1 26-EMA rows; derives W1 direction. Wire both into daily pipeline after Phase 5 indicator computation. Add `GET /api/v1/elder-impulse-market-pulse` endpoint (delegates to `ElderImpulseMarketPulseQueryService` with `IndicatorType.ELDER_IMPULSE`). Fixed `MarketPulseService` hardcoded SUPERTREND_ state counts.

### Wave 3 — Tests

- [x] **06-03-PLAN.md** — `ScanValidator` updated with ELDER_IMPULSE events/states. `ElderImpulseStateServiceIntegrationTest`: 10 tests for GREEN/RED/NEUTRAL derivation, insufficient data skipping, idempotency, W1 slope variants. `ElderImpulseMarketPulseIntegrationTest`: 3 tests. `ElderImpulseScanIntegrationTest`: 3 tests including W1+D1 AND intersection. 194 tests pass.

**Success Criteria:**
1. After pipeline run, `signal_state` contains `ELDER_IMPULSE` rows for D1 and W1 for each asset
2. `GET /api/v1/elder-impulse-market-pulse?timeframe=D1` returns GREEN/RED/NEUTRAL counts and ratio
3. `POST /api/v1/scan` with `{"indicatorType":"ELDER_IMPULSE","state":"IMPULSE_GREEN","timeframe":"D1"}` returns correct assets
4. Primary daily query (W1 GREEN AND D1 GREEN AND D1 THERMOMETER_QUIET — partially) works end-to-end
5. All existing tests pass

---

## Phase 7: Market Thermometer

**Goal:** Add the Market Thermometer indicator: daily temperature raw values stored in a dedicated table, categorical state (QUIET/HOT/SPIKE) in `signal_state`, scan/signal API support, and raw numeric values exposed in scan results.

**Requirements:** THERM-01 through THERM-09

**Design decisions:**
- `indicator_thermometer` table: asset FK, close_time, `temperature` (numeric), `temperature_ema` (numeric, nullable until 22 days of temperature history exist)
- Temperature formula: `MAX(high_today − high_yesterday, low_yesterday − low_today)`. Requires two consecutive D1 candles. Cannot compute for the very first candle per asset.
- EMA of temperature uses the same `EmaCalculator` from Phase 5 with period=22, applied to the temperature series (not the close price series)
- State derivation: if `temperature_ema` is null (< 22 days of data) → no state persisted for that asset/day. Avoids noisy early values.
- SPIKE check takes priority: `temperature > 3 × temperature_ema` → SPIKE; else `temperature > temperature_ema` → HOT; else → QUIET
- `ThermometerMatch` sealed subtype exposes `temperature` and `temperatureEma` as BigDecimal fields alongside the categorical `state`
- The profit target formula is documented in the API response description but not computed server-side (caller uses the raw values)

**Plans:** ~3 sequential

### Wave 1 — Schema + Calculator

- [x] **07-01-PLAN.md** — Flyway migration: create `indicator_thermometer` table; add `MARKET_THERMOMETER` to `indicator_type`, `THERMOMETER_QUIET`/`HOT`/`SPIKE` to `trend_state`, `THERMOMETER_CROSSED_ABOVE_EMA`/`CROSSED_BELOW_EMA`/`TRIPLE_SPIKE` to `signal_event`. Update Java enums. `ThermometerCalculator`: computes temperature series + 22-day EMA series from a list of candles.

### Wave 2 — Pipeline + API

- [x] **07-02-PLAN.md** — `ThermometerRepository` JPA repository. `ThermometerService`: incremental daily computation + persistence into `indicator_thermometer`. `ThermometerStateService`: reads latest temperature row, derives state, persists into `signal_state`. Wire into daily pipeline after D1 candle ingestion. Add `ThermometerMatch` DTO (includes `temperature`, `temperatureEma`, `state`, `timeframe`, `closeTime`); wire into scan result assembly + `ScanResult` Swagger discriminator.

### Wave 3 — Tests

- [x] **07-03-PLAN.md** — `ThermometerCalculatorTest`: known OHLC input → temperature and EMA values. `ThermometerStateServiceTest`: all state transitions including SPIKE boundary. Integration test: seed candles, run pipeline, assert `indicator_thermometer` rows. Integration test: `POST /api/v1/scan` with `MARKET_THERMOMETER` condition returns correct assets with `ThermometerMatch` including numeric values. Integration test: full daily trading query (W1 IMPULSE_GREEN AND D1 IMPULSE_GREEN AND D1 THERMOMETER_QUIET) returns correct asset.

**Success Criteria:**
1. After pipeline run, `indicator_thermometer` contains temperature + EMA rows for each active asset with ≥ 2 D1 candles
2. After pipeline run, `signal_state` contains `MARKET_THERMOMETER` rows for assets with ≥ 22 temperature values
3. `POST /api/v1/scan` with `THERMOMETER_QUIET` returns assets; matched indicators include `temperature` and `temperatureEma` numeric values
4. Full daily trading scan (W1 GREEN AND D1 GREEN AND QUIET) works end-to-end
5. All existing tests pass

---

## API Additions Summary

### New Endpoint

```
GET /api/v1/elder-impulse-market-pulse?timeframe=D1
GET /api/v1/elder-impulse-market-pulse?timeframe=W1
```

Returns breadth of GREEN/RED/NEUTRAL across all tracked assets. Same response shape as `supertrend-market-pulse` — uses existing `MarketPulseDto`.

### Scan Condition — New Indicator Types

```json
{ "timeframe": "W1", "indicatorType": "ELDER_IMPULSE", "state": "IMPULSE_GREEN" }
{ "timeframe": "D1", "indicatorType": "ELDER_IMPULSE", "state": "IMPULSE_RED" }
{ "timeframe": "D1", "indicatorType": "MARKET_THERMOMETER", "state": "THERMOMETER_QUIET" }
```

### New MatchedIndicator Subtypes

**ElderImpulseMatch:**
```json
{
  "indicatorType": "ELDER_IMPULSE",
  "timeframe": "D1",
  "state": "IMPULSE_GREEN",
  "event": "IMPULSE_TURNED_GREEN",
  "closeTime": "2026-05-28T00:00:00Z"
}
```

**ThermometerMatch:**
```json
{
  "indicatorType": "MARKET_THERMOMETER",
  "timeframe": "D1",
  "state": "THERMOMETER_QUIET",
  "temperature": 1234.56,
  "temperatureEma": 2100.00,
  "closeTime": "2026-05-28T00:00:00Z"
}
```

### Primary Daily Trading Query

```json
POST /api/v1/scan
{
  "operator": "AND",
  "conditions": [
    { "timeframe": "W1", "indicatorType": "ELDER_IMPULSE", "state": "IMPULSE_GREEN" },
    { "timeframe": "D1", "indicatorType": "ELDER_IMPULSE", "state": "IMPULSE_GREEN" },
    { "timeframe": "D1", "indicatorType": "MARKET_THERMOMETER", "state": "THERMOMETER_QUIET" }
  ]
}
```

---

## Design Notes

### Why No New State Machine

The Elder Impulse state derives from two computed slope booleans per bar — it doesn't have memory-dependent state. However, the existing `signal_state` table is still the right storage home because:

1. It records flip events (`IMPULSE_TURNED_GREEN`), which lets the scan API filter by recency (`maxDaysSinceFlip`)
2. Market breadth aggregation reads from `signal_state` — Elder Impulse breadth comes for free
3. The signal query endpoint (`GET /api/v1/signals?indicatorType=ELDER_IMPULSE`) works for free

The derivation logic is simpler than SuperTrend — two slope comparisons vs a full trend line computation — but the storage and API pattern is identical.

### EMA Reuse

The same `EmaCalculator` is used for:
- D1 13-period EMA (Impulse inertia)
- W1 26-period EMA (Impulse trend filter)
- 22-period EMA of thermometer temperature (Thermometer signal line)

Three call sites, one implementation. Period is the only variable parameter.

### Backward Compatibility

All existing API requests (v1.0, v2.0) continue to work unchanged. New enum values are additions only — no existing values renamed or removed. Flyway migrations are additive (`ALTER TYPE ... ADD VALUE`).

---
*Roadmap created: 2026-05-29*
