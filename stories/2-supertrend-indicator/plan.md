# Story 002 — Implementation Plan
SuperTrend Indicator (1D)

This plan translates requirements into deterministic implementation steps.

Follow in order.

---

## Phase 1 — Schema Foundation (Flyway)

1. Create Flyway migration:

   File:
   V2__create_indicator_supertrend.sql

2. Migration must:

   - Create ENUM type:
       CREATE TYPE supertrend_direction AS ENUM ('UP', 'DOWN');

   - Create table:
       indicator_supertrend

       id (PK)
       asset_id (FK → asset.id, NOT NULL)
       timeframe (timeframe ENUM NOT NULL)
       close_time (TIMESTAMPTZ NOT NULL)

       atr (NUMERIC NOT NULL)
       upper_band (NUMERIC NOT NULL)
       lower_band (NUMERIC NOT NULL)
       supertrend (NUMERIC NOT NULL)
       direction (supertrend_direction NOT NULL)

       created_at (TIMESTAMPTZ DEFAULT now())

   - Add unique constraint:
       (asset_id, timeframe, close_time)

3. Verify:

   - Flyway runs successfully
   - ENUM is correctly mapped
   - Unique constraint exists
   - Schema matches specification

Do not proceed until schema is correct.

---

## Phase 2 — Domain & Mapping Layer

1. Create entity:
       SuperTrendIndicator

2. Map:

   - timeframe → PostgreSQL ENUM (NAMED_ENUM)
   - direction → supertrend_direction ENUM (NAMED_ENUM)
   - Numeric fields → BigDecimal
   - No floating point usage

3. Create repository:
       SuperTrendRepository

4. Add method:
       findLatestByAssetIdAndTimeframe(...)

---

## Phase 3 — Calculation Engine

Create:

    SuperTrendCalculator

This must be pure and stateless.

### Inputs:
- Ordered List<Candle>
- atrLength (int)
- multiplier (BigDecimal)

### Output:
- List<SuperTrendResult>

### Calculation Rules:

1. True Range (TR):

   TR = max(
        high - low,
        abs(high - previousClose),
        abs(low - previousClose)
   )

2. ATR:

   - First ATR = average(TR[1..atrLength])
   - Subsequent ATR = (previousATR * (n - 1) + currentTR) / n

   Use BigDecimal division with controlled scale and rounding mode.

3. Basic Bands:

   middle = (high + low) / 2

   basicUpper = middle + multiplier * ATR
   basicLower = middle - multiplier * ATR

4. Final Bands:

   finalUpper =
       if basicUpper < previousFinalUpper
       OR previousClose > previousFinalUpper
       then basicUpper
       else previousFinalUpper

   finalLower =
       if basicLower > previousFinalLower
       OR previousClose < previousFinalLower
       then basicLower
       else previousFinalLower

5. SuperTrend:

   If previousSuperTrend == previousFinalUpper AND close <= finalUpper:
       supertrend = finalUpper
   Else if previousSuperTrend == previousFinalUpper AND close > finalUpper:
       supertrend = finalLower
   Else if previousSuperTrend == previousFinalLower AND close >= finalLower:
       supertrend = finalLower
   Else:
       supertrend = finalUpper

6. Direction:

   If supertrend == finalLower → UP
   If supertrend == finalUpper → DOWN

All computation must run oldest → newest sequentially.

No parallelization allowed.

---

## Phase 4 — Finalized Candle Filtering

Before calculation:

1. Fetch candles ordered by close_time ascending.
2. Filter:

       close_time < UTC midnight today

3. Only process finalized candles.

---

## Phase 5 — Incremental Recalculation Strategy

Default mode: Incremental.

1. Fetch latest stored SuperTrend close_time.
2. Fetch candles strictly after that timestamp.
3. Recompute from:

       max(0, lastStoredIndex - atrLength - 1)

   This ensures continuity of ATR smoothing.

Alternative mode: Full recalculation (optional flag).

---

## Phase 6 — Persistence Logic

Use native upsert:

    INSERT ... ON CONFLICT (asset_id, timeframe, close_time)

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

Log summary per asset.

---

## Phase 7 — Service Layer

Create:

    SuperTrendService

Method:

    calculateDaily()

Procedure:

1. Fetch active assets.
2. For each asset:
   - Fetch finalized candles
   - Calculate SuperTrend
   - Upsert results
3. Log summary

Each asset must run within a transaction.

---

## Phase 8 — Configuration

Add properties:

    app.indicators.supertrend.atr-length=10
    app.indicators.supertrend.multiplier=3.0

Inject into SuperTrendService.

Values must be overrideable.

---

## Phase 9 — Scheduler (Optional in this story)

Optional:

Add @Scheduled method to call calculateDaily().

Cron externalized.

Must never crash application.

---

## Phase 10 — Testing

### Unit Tests

- ATR correctness
- Band switching logic
- Direction switching logic
- Deterministic recomputation
- Incremental continuation

### Integration Test (Testcontainers)

1. Seed 30 candles.
2. Run calculation.
3. Assert stored results.
4. Run again.
5. Assert idempotency.

Optional:

Compare sample BTC data against TradingView reference.

---

## Phase 11 — Manual Verification

1. Seed BTCUSDT candles.
2. Run calculation.
3. Inspect DB.
4. Validate:
   - direction switches logically
   - no duplicate rows
   - deterministic output