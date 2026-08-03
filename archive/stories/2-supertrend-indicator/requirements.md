# Story 002 — SuperTrend Indicator Calculation

## Objective

Compute and persist a deterministic SuperTrend indicator for each asset on the 1D timeframe using finalized daily OHLC candles.

This story introduces the first derived market signal layer built on top of stored candle data.

---

## Scope

- 1D timeframe only
- Binance candles only
- Use finalized candles only
- Compute SuperTrend using:
    - ATR length = configurable (default: 10)
    - Multiplier = configurable (default: 3.0)
- Persist computed values
- Recalculation must be deterministic and idempotent

---

## Functional Requirements

### 1. Indicator Calculation

For each active asset:

- Fetch ordered 1D finalized candles
- Calculate:
    - True Range (TR)
    - Average True Range (ATR) using Wilder smoothing
    - Basic Upper Band
    - Basic Lower Band
    - Final Upper Band
    - Final Lower Band
    - SuperTrend value
    - Direction (UP or DOWN)

SuperTrend must be calculated sequentially from oldest → newest candle.

Indicator values must be derived solely from persisted candles.

No external data allowed.

---

### 2. Persistence Model

Create a new table:

    indicator_supertrend

Fields:

- id (PK)
- asset_id (FK → asset.id)
- timeframe (ENUM)
- close_time (TIMESTAMPTZ)
- atr (NUMERIC)
- upper_band (NUMERIC)
- lower_band (NUMERIC)
- supertrend (NUMERIC)
- direction (ENUM: 'UP', 'DOWN')
- created_at (TIMESTAMPTZ DEFAULT now())

Unique constraint:

    (asset_id, timeframe, close_time)

This guarantees one SuperTrend value per candle.

---

### 3. Finalized Candle Rule

SuperTrend must only be calculated for candles where:

    candle.close_time < current UTC day boundary

Partial or current-day candles must not be processed.

---

### 4. Determinism

Given identical candle history:

- Indicator values must always be identical
- Re-running calculation must not create duplicates
- Upsert logic must match Story 001 standards

If values change for the same close_time:

- Log WARNING
- Update row

---

### 5. Recalculation Strategy

Two valid modes must be supported:

1. Full Recalculation:
    - Delete all SuperTrend rows for asset
    - Recompute entire history

2. Incremental:
    - Detect last computed close_time
    - Continue forward only

Default behavior: Incremental.

---

### 6. Configuration

Properties:

    app.indicators.supertrend.atr-length=10
    app.indicators.supertrend.multiplier=3.0

Values must be injectable and overrideable.

---

### 7. Testing Requirements

Unit tests must validate:

- ATR calculation correctness
- Band switching logic
- Direction switching logic
- Deterministic recomputation

Integration test:

- Seed candle data
- Run calculation
- Assert stored values
- Run again
- Assert idempotency

---

## Non-Functional Requirements

- Calculation must be O(n)
- No quadratic loops
- BigDecimal for numeric precision
- No floating point usage
- Timezone strictly UTC
- No dependency on system default timezone

---

## Out of Scope

- Intraday timeframes
- Other indicators (RSI, MACD)
- Signal aggregation
- REST exposure
- Strategy logic

---

## Success Criteria

- SuperTrend values correctly stored for BTCUSDT
- Values match reference TradingView implementation within acceptable tolerance
- Re-running job produces zero duplicate rows
- System remains deterministic