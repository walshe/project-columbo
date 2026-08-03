# Story 003 — Signal State Detection

## Objective

Derive a deterministic signal state per asset from persisted SuperTrend indicator values.

This story converts SuperTrend output into an explicit, queryable state representation.

The resulting signal_state table is designed to support multiple indicators in future stories without schema redesign.

---

## Architectural Intent (Important)

Signal state is indicator-specific.

This story implements signal state for:

    indicator_type = 'SUPERTREND'

The schema is intentionally designed to support multiple indicators later (e.g., RSI, EMA, MACD).

Future indicators will reuse the same signal_state table.

Story 3 must NOT:

- Combine multiple indicators
- Aggregate across indicators
- Define composite market truth

That belongs in Story 4 (Market Pulse).

---

## Scope

- 1D timeframe only
- Based exclusively on persisted SuperTrend values
- No direct candle access
- Deterministic and idempotent
- No strategy logic
- No order generation

---

## Dependency Boundary

Story 3 must read from:

    indicator_supertrend

Story 3 must NOT:

- Access candle data
- Recompute SuperTrend
- Apply volatility filters
- Apply confirmation logic
- Combine indicators

It strictly interprets persisted SuperTrend rows.

---

## Core Concept

SuperTrend produces:

    direction ∈ {UP, DOWN}

Signal State Detection maps direction into:

- trend_state
- transition event

Exactly one signal_state row must exist for every finalized SuperTrend row.

---

## Functional Requirements

### 1. Indicator Type

Add column:

    indicator_type ENUM

Initial supported value:

    'SUPERTREND'

Future values may include:

    'RSI'
    'EMA'
    'MACD'
    etc.

This ensures schema scalability.

---

### 2. Signal State Definition

For each asset, timeframe, and indicator_type:

- trend_state:
    - BULLISH
    - BEARISH

- event:
    - NONE
    - BULLISH_REVERSAL
    - BEARISH_REVERSAL

Mapping:

    UP   → BULLISH
    DOWN → BEARISH

---

### 3. Persistence Model

Create table:

    signal_state

Fields:

- id (PK)
- asset_id (FK → asset.id)
- timeframe (ENUM)
- indicator_type (ENUM)
- close_time (TIMESTAMPTZ)
- trend_state (ENUM: 'BULLISH', 'BEARISH')
- event (ENUM: 'NONE', 'BULLISH_REVERSAL', 'BEARISH_REVERSAL')
- created_at (TIMESTAMPTZ DEFAULT now())

Unique constraint:

    (asset_id, timeframe, indicator_type, close_time)

This guarantees one signal row per indicator per candle.

---

### 4. Transition Rules

Given ordered SuperTrend rows:

Let:

    previous.direction
    current.direction

Rules:

If previous.direction == DOWN AND current.direction == UP:
    trend_state = BULLISH
    event = BULLISH_REVERSAL

If previous.direction == UP AND current.direction == DOWN:
    trend_state = BEARISH
    event = BEARISH_REVERSAL

Else:
    trend_state = map(current.direction)
    event = NONE

indicator_type must always be:

    'SUPERTREND'

---

### 5. First Row Initialization Rule

If no previous signal_state exists for:

    (asset_id, timeframe, indicator_type)

Then:

    trend_state = map(current.direction)
    event = NONE

The first finalized SuperTrend row must always produce a signal_state row.

---

### 6. Finalized Rule

Signal state must only be generated for SuperTrend rows where:

    close_time < current UTC day boundary

No partial or current-day rows.

---

### 7. Determinism

Given identical SuperTrend input:

- Signal state output must always be identical.
- Re-running detection must not create duplicates.
- Processing order must be strictly oldest → newest.
- Output must not depend on system timezone.

Upsert logic must follow Story 001 and Story 002 standards.

If existing row differs:

- Log WARNING
- Update row

Comparison excludes created_at.

---

### 8. Incremental Strategy

Default mode: Incremental.

1. Fetch latest stored signal_state close_time for:
       asset_id + timeframe + indicator_type

2. Fetch SuperTrend rows strictly after that.
3. Continue sequential processing.

Optional full recalculation mode supported.

Recalculation must produce identical results.

---

### 9. No Signal Filtering

Story 3 must NOT:

- Apply multi-bar confirmation
- Filter whipsaws
- Combine multiple indicators
- Generate trade signals

It reflects SuperTrend direction exactly.

---

## Non-Functional Requirements

- O(n) sequential processing
- No floating point usage
- UTC timezone only
- No dependency on system default timezone
- Must scale to 500+ assets
- Schema must support multiple indicators without redesign

---

## Testing Requirements

Unit tests must validate:

- UP → UP produces NONE
- DOWN → DOWN produces NONE
- DOWN → UP produces BULLISH_REVERSAL
- UP → DOWN produces BEARISH_REVERSAL
- First-row initialization logic
- Deterministic recomputation

Integration test:

- Seed SuperTrend rows
- Run detection
- Assert signal_state rows created
- Run again
- Assert idempotency
- Simulate modified direction
- Assert revision update path

---

## Out of Scope

- Strategy logic
- Trade entry/exit
- Portfolio aggregation
- Indicator alignment logic
- Alerts
- Notifications
- REST exposure

---

## Success Criteria

- signal_state rows mirror SuperTrend direction transitions exactly
- One row per indicator per finalized SuperTrend row
- Re-running job produces zero duplicate rows
- No partial candle processing
- Deterministic output
- Schema supports future indicators without modification