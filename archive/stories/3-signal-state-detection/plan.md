# Story 003 — Implementation Plan
Signal State Detection (Indicator-Specific)

This plan translates requirements into deterministic implementation steps.

Follow in order.

---

## Phase 1 — Schema Foundation (Flyway)

1. Create Flyway migration:

   File:
   V3__create_signal_state.sql

2. Migration must:

   - Create ENUM type:
       CREATE TYPE indicator_type AS ENUM ('SUPERTREND');

   - Create ENUM type:
       CREATE TYPE trend_state AS ENUM ('BULLISH', 'BEARISH');

   - Create ENUM type:
       CREATE TYPE signal_event AS ENUM (
           'NONE',
           'BULLISH_REVERSAL',
           'BEARISH_REVERSAL'
       );

   - Create table:
       signal_state

       id (PK)
       asset_id (FK → asset.id, NOT NULL)
       timeframe (timeframe ENUM NOT NULL)
       indicator_type (indicator_type NOT NULL)
       close_time (TIMESTAMPTZ NOT NULL)

       trend_state (trend_state NOT NULL)
       event (signal_event NOT NULL)

       created_at (TIMESTAMPTZ DEFAULT now())

   - Add unique constraint:
       (asset_id, timeframe, indicator_type, close_time)

3. Verify:

   - Flyway runs successfully
   - ENUM mappings correct
   - Unique constraint exists
   - Schema matches specification

Do not proceed until schema is correct.

---

## Phase 2 — Domain & Mapping Layer

1. Create entity:
       SignalState

2. Map:

   - timeframe → PostgreSQL ENUM (NAMED_ENUM)
   - indicator_type → indicator_type ENUM (NAMED_ENUM)
   - trend_state → trend_state ENUM (NAMED_ENUM)
   - event → signal_event ENUM (NAMED_ENUM)

3. Create repository:
       SignalStateRepository

4. Required methods:

   - findLatestByAssetIdAndTimeframeAndIndicatorType(...)
   - findAllByAssetIdAndTimeframeAndIndicatorTypeOrderByCloseTimeAsc(...)

Ensure no candle or indicator recomputation dependencies.

---

## Phase 3 — State Transition Engine (Pure)

Create:

    SignalStateCalculator

This must be pure and stateless.

### Input:
- Ordered List<SuperTrendIndicator>

### Output:
- List<SignalStateResult>

### Rules:

1. indicator_type must be constant:

       SUPERTREND

2. Process oldest → newest sequentially.

3. First-row rule:

   If no previous state exists:
       trend_state = map(direction)
       event = NONE

4. Transition rules:

   If previous.direction == DOWN AND current.direction == UP:
       trend_state = BULLISH
       event = BULLISH_REVERSAL

   If previous.direction == UP AND current.direction == DOWN:
       trend_state = BEARISH
       event = BEARISH_REVERSAL

   Else:
       trend_state = map(current.direction)
       event = NONE

No filtering.
No smoothing.
No confirmation logic.

O(n) sequential pass only.

---

## Phase 4 — Finalized Filtering

Before processing:

1. Fetch SuperTrend rows ordered ascending.
2. Filter rows where:

       close_time < UTC midnight boundary

3. Only finalized rows may be processed.

---

## Phase 5 — Incremental Processing

Default mode: Incremental.

1. Fetch latest stored signal_state close_time for:

       asset_id + timeframe + indicator_type

2. Fetch SuperTrend rows strictly after that.

3. For transition correctness:

   Retrieve the previous direction from the latest stored state
   to determine whether the first incremental row triggers a reversal.

4. If no prior state exists:
       start from first finalized SuperTrend row.

Optional full recalculation mode supported.

Recalculation must be deterministic.

---

## Phase 6 — Persistence Logic

Use native upsert:

    INSERT ... ON CONFLICT (asset_id, timeframe, indicator_type, close_time)

Rules:

- If identical:
    → skip

- If different:
    → log WARNING
    → update

Exclude created_at from comparison.

Track:

- inserted_count
- updated_count
- skipped_count

Log per-asset summary.

---

## Phase 7 — Service Layer

Create:

    SignalStateService

Method:

    detectDaily()

Procedure:

1. Fetch active assets.
2. For each asset:
   - Fetch finalized SuperTrend rows
   - Apply incremental rules
   - Run SignalStateCalculator
   - Persist results with indicator_type = SUPERTREND
3. Log summary.

Each asset must execute within a transaction.

---

## Phase 8 — Scheduler (Optional)

Optional:

Add @Scheduled method:

    detectDaily()

Cron externalized in configuration.

Scheduler must:

- Catch exceptions
- Log failures
- Never crash application

---

## Phase 9 — Testing

### Unit Tests

- UP → UP produces NONE
- DOWN → DOWN produces NONE
- DOWN → UP produces BULLISH_REVERSAL
- UP → DOWN produces BEARISH_REVERSAL
- First-row initialization logic
- Deterministic recomputation

### Integration Test (Testcontainers)

1. Seed SuperTrend rows.
2. Run detection.
3. Assert signal_state rows created.
4. Run again.
5. Assert idempotency.
6. Modify direction.
7. Assert revision update path.

---

## Phase 10 — Manual Verification

1. Seed SuperTrend history for BTCUSDT.
2. Run detection.
3. Inspect DB.
4. Validate:
   - Reversal events match direction flips.
   - One row per indicator per finalized SuperTrend row.
   - Deterministic behavior.