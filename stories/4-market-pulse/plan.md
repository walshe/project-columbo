# Story 004 — Implementation Plan
Market Breadth Snapshot Across Assets (1D, SUPERTREND)

This plan translates requirements into deterministic implementation steps.

Follow in order.

---

## Phase 1 — Schema Foundation (Flyway)

1. Create Flyway migration:

   File:
   V4__create_market_breadth_snapshot.sql

2. Migration must create table:

   market_breadth_snapshot

   Columns:
   - id (PK)
   - timeframe (timeframe ENUM NOT NULL)
   - indicator_type (indicator_type ENUM NOT NULL)
   - snapshot_close_time (TIMESTAMPTZ NOT NULL)

   - bullish_count (INT NOT NULL)
   - bearish_count (INT NOT NULL)
   - missing_count (INT NOT NULL)
   - total_assets (INT NOT NULL)

   - bullish_ratio (NUMERIC NOT NULL)

   - created_at (TIMESTAMPTZ DEFAULT now())

3. Add unique constraint:

   (timeframe, indicator_type, snapshot_close_time)

4. Verify:

   - Flyway runs successfully
   - Schema exists and matches
   - Unique constraint exists

Do not proceed until schema is correct.

---

## Phase 2 — Domain & Repository Layer

1. Create entity:

   MarketBreadthSnapshot

2. Map:
   - timeframe → PostgreSQL ENUM (NAMED_ENUM)
   - indicator_type → PostgreSQL ENUM (NAMED_ENUM)
   - snapshot_close_time → OffsetDateTime (UTC)
   - bullish_ratio → BigDecimal
   - counts → int

3. Create repository:

   MarketBreadthSnapshotRepository

4. Implement methods:
   - findLatestByTimeframeAndIndicatorType(...)
   - findByTimeframeAndIndicatorTypeAndSnapshotCloseTime(...)

---

## Phase 3 — Determine Snapshot Days to Compute (Incremental)

Implement snapshot selection logic:

1. Compute UTC finalized boundary:
   - utcMidnightToday = start of current day in UTC

2. Determine:
   - latest stored snapshot_close_time for (timeframe, indicator_type)
   - latest available signal_state close_time for (timeframe, indicator_type) where close_time < utcMidnightToday

3. Build ordered list of missing snapshot_close_time values:
   - If latest snapshot exists:
       generate days after latest snapshot up to latest available
   - If no snapshot exists:
       start from earliest available finalized close_time in signal_state

4. Ensure list is strictly oldest → newest.

---

## Phase 4 — Breadth Calculator (Pure)

Create:

   MarketBreadthCalculator

Pure/stateless logic.

Inputs:
- total_active_assets (int)
- signal_states_at_close_time: List<SignalState> (for the snapshot_close_time)

Outputs:
- bullish_count
- bearish_count
- missing_count
- total_assets
- bullish_ratio

Rules:
- bullish_count = count(trend_state == BULLISH)
- bearish_count = count(trend_state == BEARISH)
- total_assets = total_active_assets
- missing_count = total_assets - bullish_count - bearish_count
- bullish_ratio = bullish_count / total_assets (BigDecimal with explicit scale + rounding)

No NEUTRAL state.

---

## Phase 5 — Fetch Data for a Snapshot Day

For each snapshot_close_time:

1. Fetch total active assets:
   - assetRepository.countByActiveTrue()

2. Fetch signal_state rows for exactly:
   - timeframe = 1D
   - indicator_type = SUPERTREND
   - close_time = snapshot_close_time

3. Pass results to MarketBreadthCalculator.

---

## Phase 6 — Persistence Logic (Idempotent Upsert)

Persist one snapshot per snapshot_close_time using:

   INSERT ... ON CONFLICT (timeframe, indicator_type, snapshot_close_time)

Rules:
- If identical (excluding created_at) → skip
- If different → log WARNING → update

Track:
- inserted_count
- updated_count
- skipped_count

---

## Phase 7 — Service Layer

Create:

   MarketBreadthService

Method:

   computeDaily()

Procedure:
1. Set timeframe = 1D
2. Set indicator_type = SUPERTREND
3. Determine snapshot_close_time list (Phase 3)
4. For each snapshot_close_time:
   - fetch active asset count
   - fetch signal_state rows at that close_time
   - compute snapshot
   - upsert snapshot
5. Log summary

Must be deterministic and safe to rerun.

---

## Phase 8 — Scheduler (Optional)

Optional:
- Add @Scheduled method calling MarketBreadthService.computeDaily()
- Cron externalized in application config
- Catch and log exceptions
- Must not crash application

---

## Phase 9 — Testing

### Unit Tests
- MarketBreadthCalculator:
  - 2 bullish, 1 bearish, total 3 → ratio 0.666...
  - total 5 assets, only 3 states present → missing_count = 2
- Snapshot selection logic:
  - generates only missing days when snapshots exist
  - starts from earliest available day when none exist

### Integration Tests (Testcontainers)
1. Seed:
   - active assets
   - signal_state for multiple days

2. Run computeDaily()
3. Assert snapshots exist for expected days and counts match
4. Run computeDaily() again
5. Assert idempotency (no duplicates)
6. Modify signal_state row
7. Re-run and assert WARNING + updated snapshot

---

## Phase 10 — Manual Verification

1. Seed a small active universe (e.g., 5 assets).
2. Ensure signal_state exists for last few finalized days.
3. Run computeDaily()
4. Inspect DB:
   - snapshots created
   - counts add up
   - bullish_ratio correct
   - missing_count behaves as expected