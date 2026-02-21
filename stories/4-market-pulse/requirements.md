# Story 004 — Market Pulse Aggregation

## 1. Objective

Aggregate indicator-specific signal states into a unified asset-level
market pulse per timeframe.

The market pulse represents the combined directional bias of multiple
indicators for a given asset and timeframe.

This story introduces multi-indicator coordination while preserving
indicator independence.

---

## 2. Scope

This story:

- Reads finalized rows from `signal_state`
- Aggregates across indicator_type
- Produces one row per:
    asset_id + timeframe + close_time
- Persists aggregated result into `market_pulse` table

This story does NOT:

- Recompute indicators
- Recompute signal_state
- Introduce trading logic
- Trigger orders
- Define strategies

This is strictly aggregation.

---

## 3. Functional Requirements

### 3.1 Aggregation Inputs

For a given:

- asset_id
- timeframe
- close_time

Fetch all signal_state rows where:

- indicator_type IN enabled indicators
- close_time < finalized boundary (UTC midnight for D1)

Aggregation must only consider finalized signal_state rows.

---

### 3.2 Initial Indicator Set

For Story 4:

Enabled indicators:

- SUPERTREND

Future stories may add:

- RSI
- EMA
- MACD
- etc.

The aggregation logic must support N indicators.

---

### 3.3 Aggregation Rules (Version 1 — Simple Majority)

Define:

    bullish_count
    bearish_count

Compute:

If bullish_count > bearish_count:
    pulse_state = BULLISH

If bearish_count > bullish_count:
    pulse_state = BEARISH

If equal:
    pulse_state = NEUTRAL

---

### 3.4 Event Detection

Define pulse_event:

- NONE
- BULLISH_REVERSAL
- BEARISH_REVERSAL

Rules:

If previous pulse_state == BEARISH
   AND current pulse_state == BULLISH
   → BULLISH_REVERSAL

If previous pulse_state == BULLISH
   AND current pulse_state == BEARISH
   → BEARISH_REVERSAL

Else:
   → NONE

NEUTRAL transitions do NOT generate reversal events.

---

### 3.5 Finalization Rule

Only aggregate for:

    close_time < current UTC day boundary

Never aggregate the current open daily candle.

---

### 3.6 Determinism

Aggregation must be:

- Deterministic
- Idempotent
- Recomputable

Full recalculation must produce identical results.

---

## 4. Data Model

### 4.1 ENUM Definitions

Create ENUM:

    pulse_state AS ENUM (
        'BULLISH',
        'BEARISH',
        'NEUTRAL'
    )

Create ENUM:

    pulse_event AS ENUM (
        'NONE',
        'BULLISH_REVERSAL',
        'BEARISH_REVERSAL'
    )

---

### 4.2 market_pulse Table

Columns:

- id (PK)
- asset_id (FK → asset.id, NOT NULL)
- timeframe (ENUM, NOT NULL)
- close_time (TIMESTAMPTZ, NOT NULL)

- bullish_count (INT NOT NULL)
- bearish_count (INT NOT NULL)
- total_indicators (INT NOT NULL)

- pulse_state (pulse_state ENUM NOT NULL)
- event (pulse_event ENUM NOT NULL)

- created_at (TIMESTAMPTZ DEFAULT now())

Unique constraint:

    (asset_id, timeframe, close_time)

---

## 5. Incremental Processing

Default mode: Incremental

For each asset + timeframe:

1. Fetch latest stored market_pulse close_time
2. Fetch signal_state rows strictly after that
3. For first incremental row:
    Retrieve previous pulse_state for reversal continuity
4. Aggregate sequentially oldest → newest

Full recalculation mode supported.

---

## 6. Idempotent Persistence

Use:

    INSERT ... ON CONFLICT (asset_id, timeframe, close_time)

Rules:

- If identical → skip
- If different → log WARNING → update

Track:

- inserted_count
- updated_count
- skipped_count

---

## 7. Service Layer

Create:

    MarketPulseService

Method:

    aggregateDaily()

Steps:

1. Fetch active assets
2. Fetch finalized signal_state rows
3. Group by close_time
4. Apply aggregation rules
5. Detect pulse transitions
6. Persist via upsert
7. Log summary

Each asset processed within transaction.

---

## 8. Non-Functional Requirements

- O(n) sequential processing
- No cross-asset coupling
- No cross-timeframe coupling
- No indicator recalculation
- Aggregation must be pure and testable
- Ready for N indicators

---

## 9. Testing Requirements

### Unit Tests

- Single indicator → mirrors signal_state
- Two bullish → pulse BULLISH
- Two bearish → pulse BEARISH
- 1 bullish + 1 bearish → NEUTRAL
- Reversal detection works
- Neutral transitions do not fire reversal

### Integration Tests

- Seed signal_state rows
- Run aggregation
- Validate market_pulse rows
- Run again
- Assert idempotency
- Modify signal_state
- Assert revision update

---

## 10. Out of Scope (Future Stories)

- Indicator weighting
- Confidence scoring
- Strategy definitions
- Trade execution
- Multi-timeframe confirmation
- Alerting
- Risk management