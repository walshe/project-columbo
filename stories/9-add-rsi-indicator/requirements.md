# Story 009 — Add RSI Indicator

## 1. Objective

Introduce Relative Strength Index (RSI) as a first-class indicator in the system, mirroring the existing SuperTrend pattern.

This story adds:

- A new `indicator_rsi` table
- RSI calculation and persistence logic
- Signal detection for RSI-based state and events
- Integration into `signal_state` aggregation
- Enablement for `MarketPulseService` to compute pulses per indicator type

## 2. Scope

This story:

- ✅ Adds RSI data ingestion and computation pipeline
- ✅ Persists RSI values daily per asset/timeframe
- ✅ Generates `signal_state` transitions (`ABOVE_60` / `BELOW_40` / `NEUTRAL`)
- ✅ Detects events (`CROSSED_ABOVE_60` / `CROSSED_BELOW_40` / `NONE`)
- ✅ Extends `indicator_type` ENUM with 'RSI'

It does not:

- Integrate RSI into market-pulse aggregation (already handled generically)
- Perform trading decisions or combined indicator logic
- Expose new REST endpoints (uses existing `/api/v1/indicators/{type}` conventions)

## 3. Functional Requirements

### 3.1 RSI Computation

For each asset and timeframe (D1):

Compute RSI using standard 14-period Wilder’s RSI:

- RS = `avg_gain / avg_loss`
- RSI = `100 - (100 / (1 + RS))`

Requires last 15 close prices (14 intervals).
Use closing prices from the `ohlc` table.

### 3.2 RSI Value Persistence

Create one row per:
`asset_id + timeframe + close_time`

If a row exists with identical RSI value, skip insert.

## 4. Data Model

### 4.1 ENUM Updates

Extend existing ENUMs:

**indicator_type**
```sql
ALTER TYPE indicator_type ADD VALUE 'RSI';
```

**trend_state**
```sql
ALTER TYPE trend_state ADD VALUE IF NOT EXISTS 'ABOVE_60';
ALTER TYPE trend_state ADD VALUE IF NOT EXISTS 'BELOW_40';
ALTER TYPE trend_state ADD VALUE IF NOT EXISTS 'NEUTRAL';
```

**event**
```sql
ALTER TYPE event ADD VALUE IF NOT EXISTS 'CROSSED_ABOVE_60';
ALTER TYPE event ADD VALUE IF NOT EXISTS 'CROSSED_BELOW_40';
```

### 4.2 New Table — indicator_rsi

```sql
CREATE TABLE indicator_rsi (
    id              BIGSERIAL PRIMARY KEY,
    asset_id        BIGINT NOT NULL REFERENCES asset(id),
    timeframe       timeframe NOT NULL,
    close_time      TIMESTAMPTZ NOT NULL,
    rsi_value       NUMERIC(10,4) NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT now(),

    UNIQUE (asset_id, timeframe, close_time)
);
```

**Indexes:**
- `(asset_id, timeframe, close_time DESC)`

### 4.3 Reuse signal_state for RSI

Each RSI signal contributes to the shared `signal_state` table:

- `indicator_type = RSI`
- `trend_state = ABOVE_60 | BELOW_40 | NEUTRAL`
- `event = CROSSED_ABOVE_60 | CROSSED_BELOW_40 | NONE`

## 5. Signal Derivation Logic

### 5.1 Trend State Classification

For each RSI value:

| Condition | trend_state |
| :--- | :--- |
| `rsi_value >= 60` | `ABOVE_60` |
| `rsi_value <= 40` | `BELOW_40` |
| `otherwise` | `NEUTRAL` |

### 5.2 Event Detection

Compare current state to previous state:

| Previous | Current | event |
| :--- | :--- | :--- |
| `BELOW_40` | `ABOVE_60` | `CROSSED_ABOVE_60` |
| `BELOW_40` | `NEUTRAL` | `NONE` |
| `NEUTRAL` | `ABOVE_60` | `CROSSED_ABOVE_60` |
| `ABOVE_60` | `BELOW_40` | `CROSSED_BELOW_40` |
| `ABOVE_60` | `NEUTRAL` | `NONE` |
| `otherwise` | `otherwise` | `NONE` |

Equivalent to how SuperTrend emits `BULLISH_REVERSAL` / `BEARISH_REVERSAL`.

## 6. Processing Flow

### 6.1 Service Layer

Add `RsiComputationService`:

- **Inputs:** `asset_id`, `timeframe`
- Fetch recent OHLC closes
- Compute RSI
- Persist to `indicator_rsi`
- Call `SignalStateService.upsertRsiState(...)`

### 6.2 Scheduling Integration

Extend existing `IndicatorComputationScheduler`:

- After SuperTrend computation → trigger RSI computation
- Skip if RSI row already exists for `close_time`

### 6.3 Integration with signal_state

Add method:
```java
signalStateService.computeForRsi(asset, timeframe, closeTime, rsiValue);
```

## 7. Testing Requirements

### 7.1 Unit Tests

- RSI calculation correctness (14-period)
- State classification correctness (`ABOVE_60` / `BELOW_40` / `NEUTRAL`)
- Event detection transitions

### 7.2 Integration Tests

- Given seeded OHLC data → compute RSI → persist row
- Generate corresponding `signal_state` row
- Verify idempotency on re-run
- Verify event detection between consecutive days

## 8. Future Enhancements (Out of Scope)

- Adjustable RSI period via configuration
- Upper/lower bounds configurable (default 60/40)
- Combined indicator correlation (e.g. SuperTrend + RSI)
- Exposure via REST endpoint `/api/v1/indicators/rsi`
- Visualization layer integration

## 9. Completion Criteria

- ✅ RSI values computed and persisted daily
- ✅ `signal_state` rows generated for RSI
- ✅ Events (`CROSSED_ABOVE_60`, `CROSSED_BELOW_40`) correctly emitted
- ✅ MarketPulse automatically includes RSI in aggregation
- ✅ All tests green and deterministic