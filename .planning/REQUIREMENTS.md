# Requirements: Project Colombo — v3.0 Elder Impulse + Market Thermometer

**Defined:** 2026-05-29
**Core Value:** Give traders a clear, up-to-date view of trend signals across a crypto asset universe — across multiple timeframes — so they can make faster, better-informed decisions. Specifically: surface the Elder Impulse System's GREEN/RED/NEUTRAL permission states and the Market Thermometer's entry-timing signal to reduce bad trades and improve entry quality.

---

## v1.0 Requirements (Complete)

All 16 v1.0 requirements satisfied.

| ID | Requirement | Phase | Status |
|----|-------------|-------|--------|
| CNDL-01 | Derive W1 candles from D1 rollup (Mon open → Sun close, UTC) | 1 | ✓ Complete |
| CNDL-02 | Partial weeks not stored as W1 candles | 1 | ✓ Complete |
| CNDL-03 | Rollup is incremental | 1 | ✓ Complete |
| CNDL-04 | Rollup mechanism is timeframe-generic | 1 | ✓ Complete |
| CNDL-05 | DB schema supports W1 as valid Timeframe | 1 | ✓ Complete |
| INDC-01 | SuperTrend computed on W1 | 2 | ✓ Complete |
| INDC-02 | RSI computed on W1 | 2 | ✓ Complete |
| INDC-03 | W1 indicator computation is incremental | 2 | ✓ Complete |
| SGNL-01 | Signal state detection on W1 | 2 | ✓ Complete |
| SGNL-02 | Market breadth snapshot for W1 | 2 | ✓ Complete |
| PIPE-01 | Daily pipeline includes W1 derivation + indicator + signal + pulse pass | 3 | ✓ Complete |
| PIPE-02 | D1 pipeline pass unchanged | 3 | ✓ Complete |
| PIPE-03 | Pipeline run tracking reflects W1 processing | 3 | ✓ Complete |
| API-01 | Market pulse endpoint returns W1 data | 3 | ✓ Complete |
| API-02 | Signal query endpoint supports W1 | 3 | ✓ Complete |
| API-03 | Scan endpoint supports W1 in scan conditions | 3 | ✓ Complete |

---

## v2.0 Requirements (Complete)

All 13 v2.0 requirements satisfied.

| ID | Requirement | Phase | Status |
|----|-------------|-------|--------|
| SCAN-01 | `ScanCondition` gains optional `timeframe` field | 4 | ✓ Complete |
| SCAN-02 | `ScanRequest.timeframe` becomes optional fallback | 4 | ✓ Complete |
| SCAN-03 | Validation rejects conditions with no resolvable timeframe | 4 | ✓ Complete |
| SCAN-04 | Backward compatibility — existing single-timeframe requests unchanged | 4 | ✓ Complete |
| SCAN-05 | AND logic applies across all conditions regardless of timeframe | 4 | ✓ Complete |
| SCAN-06 | Each condition evaluated against latest finalized close time for its timeframe | 4 | ✓ Complete |
| SCAN-07 | Asset intersection operates on timeframe-agnostic asset IDs | 4 | ✓ Complete |
| SCAN-08 | `MatchedIndicator` subtypes include a `timeframe` field | 4 | ✓ Complete |
| SCAN-09 | `ScanResponse` echoes resolved timeframes; top-level nullable when omitted | 4 | ✓ Complete |
| SCAN-10 | TradingView URL uses highest-granularity timeframe among matched conditions | 4 | ✓ Complete |
| SCAN-11 | `ScanValidatorTest` covers per-condition, fallback, rejection cases | 4 | ✓ Complete |
| SCAN-12 | Integration test: W1 BULLISH AND D1 BULLISH — correct assets + per-timeframe indicators | 4 | ✓ Complete |
| SCAN-13 | All pre-existing single-timeframe integration tests pass unchanged | 4 | ✓ Complete |

---

## v3.0 Requirements — Elder Impulse + Market Thermometer

### EMA Foundation (Phase 5)

- [ ] **EMA-01**: `indicator_ema` table stores EMA values per asset, timeframe, period, and close_time. Schema mirrors `indicator_rsi` (asset FK, timeframe, close_time, numeric value, unique constraint). — Phase 5
- [ ] **EMA-02**: `EmaCalculator` computes EMA series using Wilder's / standard exponential smoothing. First value = SMA of the first N periods; subsequent values use the standard EMA formula. Parameterised by period. — Phase 5
- [ ] **EMA-03**: 13-period EMA computed incrementally on D1 candles. — Phase 5
- [ ] **EMA-04**: 26-period EMA computed incrementally on W1 candles. — Phase 5

### MACD Foundation (Phase 5)

- [ ] **MACD-01**: `indicator_macd` table stores `macd_line`, `signal_line`, and `histogram` per asset, timeframe, and close_time. Unique constraint on (asset_id, timeframe, close_time). — Phase 5
- [ ] **MACD-02**: `MacdCalculator` computes MACD 12-26-9: fast EMA(12), slow EMA(26), MACD line = fast − slow, signal line = EMA(9) of MACD line, histogram = MACD line − signal line. — Phase 5
- [ ] **MACD-03**: MACD computed incrementally on D1 candles. — Phase 5

### Elder Impulse State (Phase 6)

- [ ] **IMPL-01**: `IndicatorType` enum gains `ELDER_IMPULSE` value; `TrendState` gains `IMPULSE_GREEN`, `IMPULSE_RED`, `IMPULSE_NEUTRAL`; `SignalEvent` gains `IMPULSE_TURNED_GREEN`, `IMPULSE_TURNED_RED`, `IMPULSE_TURNED_NEUTRAL`. All prefixed per convention. Flyway migration adds DB enum values. — Phase 6
- [ ] **IMPL-02**: Elder Impulse D1 state derived daily from 13-EMA slope and MACD-H slope:
  - Both rising → `IMPULSE_GREEN`
  - Both falling → `IMPULSE_RED`
  - Diverging → `IMPULSE_NEUTRAL`
  Flip event stored on state change (e.g., `IMPULSE_TURNED_GREEN` when transitioning to GREEN). — Phase 6
- [ ] **IMPL-03**: W1 strategic direction derived daily from 26-EMA slope:
  - Rising → `IMPULSE_GREEN` (W1 is bullish permission)
  - Falling → `IMPULSE_RED` (W1 is bearish permission)
  - Flat (slope < threshold) → `IMPULSE_NEUTRAL`
  Stored in `signal_state` with `IndicatorType.ELDER_IMPULSE` and `Timeframe.W1`. — Phase 6
- [ ] **IMPL-04**: Elder Impulse state stored in the existing `signal_state` table (no new table). The existing `TrendState`/`SignalEvent`/flip detection machinery handles it without modification. — Phase 6
- [ ] **IMPL-05**: Scan API supports `ELDER_IMPULSE` as an `indicatorType` in `ScanCondition`; `state` filter accepts `IMPULSE_GREEN`, `IMPULSE_RED`, `IMPULSE_NEUTRAL`. — Phase 6
- [ ] **IMPL-06**: Signal query endpoint (`GET /api/v1/signals?indicatorType=ELDER_IMPULSE&timeframe=D1`) returns current impulse states across all assets. — Phase 6
- [ ] **IMPL-07**: `GET /api/v1/elder-impulse-market-pulse?timeframe=D1` returns market breadth of GREEN/RED/NEUTRAL counts and ratios. Market breadth snapshot computed daily and stored with `IndicatorType.ELDER_IMPULSE`. — Phase 6
- [ ] **IMPL-08**: `MatchedIndicator` gains `ElderImpulseMatch` subtype returned in scan results when `indicatorType=ELDER_IMPULSE`. — Phase 6

### Market Thermometer (Phase 7)

- [ ] **THERM-01**: `indicator_thermometer` table stores `temperature` (raw daily value) and `temperature_ema` (22-day EMA of temperature) per asset and close_time. — Phase 7
- [ ] **THERM-02**: `ThermometerCalculator` computes temperature = `MAX(High_today − High_yesterday, Low_yesterday − Low_today)`. Always positive. — Phase 7
- [ ] **THERM-03**: `ThermometerCalculator` computes 22-day EMA of the temperature series as the signal line. — Phase 7
- [ ] **THERM-04**: Thermometer computed incrementally on D1 candles. Requires at least 2 consecutive daily candles (temperature needs yesterday's OHLC). — Phase 7
- [ ] **THERM-05**: `IndicatorType` enum gains `MARKET_THERMOMETER`; `TrendState` gains `THERMOMETER_QUIET`, `THERMOMETER_HOT`, `THERMOMETER_SPIKE`; `SignalEvent` gains `THERMOMETER_CROSSED_ABOVE_EMA`, `THERMOMETER_CROSSED_BELOW_EMA`, `THERMOMETER_TRIPLE_SPIKE`. All prefixed per convention. — Phase 7
- [ ] **THERM-06**: Thermometer state derived daily:
  - `temperature < temperature_ema` → `THERMOMETER_QUIET` (good entry timing)
  - `temperature > temperature_ema` → `THERMOMETER_HOT` (caution — slippage risk)
  - `temperature > 3 × temperature_ema` → `THERMOMETER_SPIKE` (panic/euphoria — take profits)
  Stored in `signal_state` with `IndicatorType.MARKET_THERMOMETER` and `Timeframe.D1`. — Phase 7
- [ ] **THERM-07**: Scan API supports `MARKET_THERMOMETER` as an `indicatorType`; `state` filter accepts `THERMOMETER_QUIET`, `THERMOMETER_HOT`, `THERMOMETER_SPIKE`. — Phase 7
- [ ] **THERM-08**: Signal query endpoint supports `indicatorType=MARKET_THERMOMETER`. — Phase 7
- [ ] **THERM-09**: Scan response exposes raw thermometer numeric values (`temperature`, `temperatureEma`) alongside categorical state in `MatchedIndicator` for `MARKET_THERMOMETER`. API exposes these for profit target calculations (long target = yesterday's high + EMA; short target = yesterday's low − EMA). — Phase 7

---

## Daily Trading Query

The primary use case this milestone enables:

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

Returns assets where:
- Weekly trend is bullish (Screen 1: W1 26-EMA rising)
- Daily impulse is green permission (Screen 2: D1 13-EMA + MACD-H both rising)
- Market is quiet for entry (D1 temperature below 22-day EMA)

---

## Out of Scope (v3.0)

| Feature | Reason |
|---------|--------|
| Triple Screen Force Index (Screen 2 oscillator) | Impulse System is simpler and sufficient; Force Index adds complexity without proportional signal improvement |
| H4 base timeframe | Future milestone |
| Screen 3 intraday entry | Belongs to TradingView MCP, not Colombo |
| Alert / notification delivery | API-only for v3.0 |
| Frontend / UI | API-only |
| Fetching W1 candles from Binance | Rolled up from D1 for internal consistency |
| MACD on W1 | W1 impulse only needs 26-EMA slope; MACD-H not required at weekly granularity per Elder's system |

---

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| EMA-01 | 5 | ○ Pending |
| EMA-02 | 5 | ○ Pending |
| EMA-03 | 5 | ○ Pending |
| EMA-04 | 5 | ○ Pending |
| MACD-01 | 5 | ○ Pending |
| MACD-02 | 5 | ○ Pending |
| MACD-03 | 5 | ○ Pending |
| IMPL-01 | 6 | ○ Pending |
| IMPL-02 | 6 | ○ Pending |
| IMPL-03 | 6 | ○ Pending |
| IMPL-04 | 6 | ○ Pending |
| IMPL-05 | 6 | ○ Pending |
| IMPL-06 | 6 | ○ Pending |
| IMPL-07 | 6 | ○ Pending |
| IMPL-08 | 6 | ○ Pending |
| THERM-01 | 7 | ○ Pending |
| THERM-02 | 7 | ○ Pending |
| THERM-03 | 7 | ○ Pending |
| THERM-04 | 7 | ○ Pending |
| THERM-05 | 7 | ○ Pending |
| THERM-06 | 7 | ○ Pending |
| THERM-07 | 7 | ○ Pending |
| THERM-08 | 7 | ○ Pending |
| THERM-09 | 7 | ○ Pending |

**Coverage:** 0/24 v3.0 requirements complete (3 phases pending)

---
*Requirements defined: 2026-05-29*
