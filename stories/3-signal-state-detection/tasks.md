# Story 004 — Tasks

## Phase 1 — Schema

- [ ] Create Flyway migration `V4__create_market_breadth_snapshot.sql`
- [ ] Create table `market_breadth_snapshot` with:
  - [ ] timeframe (timeframe ENUM NOT NULL)
  - [ ] indicator_type (indicator_type ENUM NOT NULL)
  - [ ] snapshot_close_time (TIMESTAMPTZ NOT NULL)
  - [ ] bullish_count (INT NOT NULL)
  - [ ] bearish_count (INT NOT NULL)
  - [ ] missing_count (INT NOT NULL)
  - [ ] total_assets (INT NOT NULL)
  - [ ] bullish_ratio (NUMERIC with explicit precision/scale, NOT NULL)
  - [ ] created_at (TIMESTAMPTZ DEFAULT now())
- [ ] Add unique constraint `(timeframe, indicator_type, snapshot_close_time)`
- [ ] Start application and verify Flyway runs successfully
- [ ] Inspect schema manually (columns + constraint)

---

## Phase 2 — Domain & Repository

- [ ] Create `MarketBreadthSnapshot` entity
- [ ] Map `timeframe` as PostgreSQL named enum (NAMED_ENUM)
- [ ] Map `indicator_type` as PostgreSQL named enum (NAMED_ENUM)
- [ ] Map `snapshotCloseTime` as `OffsetDateTime` (TIMESTAMPTZ)
- [ ] Map `bullishRatio` as `BigDecimal`
- [ ] Mirror unique constraint in entity mapping:
      `(timeframe, indicator_type, snapshot_close_time)`

- [ ] Create `MarketBreadthSnapshotRepository`
- [ ] Implement:
      `findTopByTimeframeAndIndicatorTypeOrderBySnapshotCloseTimeDesc(...)`
- [ ] Implement:
      `findByTimeframeAndIndicatorTypeAndSnapshotCloseTime(...)`
- [ ] Add basic repository test:
  - [ ] insert + fetch latest snapshot
  - [ ] verify unique constraint behavior

---

## Phase 3 — Snapshot Day Selection (Incremental)

- [ ] Implement UTC day boundary calculation (UTC midnight)
- [ ] Query latest stored snapshot_close_time via:
      `findTopByTimeframeAndIndicatorTypeOrderBySnapshotCloseTimeDesc(...)`
- [ ] Query latest available finalized close_time in `signal_state` for `(timeframe, indicator_type)`
- [ ] If no snapshots exist:
      determine earliest available finalized close_time in `signal_state`
- [ ] Build ordered list of missing snapshot_close_time values (oldest → newest)
- [ ] Ensure snapshot_close_time values match `signal_state.close_time` instants exactly
- [ ] Unit test snapshot selection logic

---

## Phase 4 — MarketBreadthCalculator (Pure)

- [ ] Create `MarketBreadthCalculator` (pure/stateless)
- [ ] Compute `bullish_count` from trend_state=BULLISH
- [ ] Compute `bearish_count` from trend_state=BEARISH
- [ ] Compute `total_assets` from active asset count
- [ ] Compute `missing_count = total_assets - bullish_count - bearish_count`
- [ ] Compute `bullish_ratio = bullish_count / total_assets`
      using BigDecimal with explicit rounding/scale
- [ ] Unit test calculator (including missing_count behavior)

---

## Phase 5 — Data Retrieval Per Snapshot Day

- [ ] Add `assetRepository.countByActiveTrue()`
- [ ] Add query method on `SignalStateRepository` to fetch rows for:
      `(timeframe=1D, indicator_type=SUPERTREND, close_time = snapshot_close_time)`
- [ ] Ensure query uses exact close_time equality (not range)
- [ ] Build counts from returned rows (avoid per-asset API calls / loops)

---

## Phase 6 — Persistence / Upsert Logic

- [ ] Implement INSERT ... ON CONFLICT upsert for `market_breadth_snapshot`
- [ ] Compare existing vs incoming excluding created_at
- [ ] If identical → skipped_count++
- [ ] If different → log WARNING and update → updated_count++
- [ ] inserted_count++ on first insert
- [ ] Log per-run summary

---

## Phase 7 — Service Layer

- [ ] Create `MarketBreadthService`
- [ ] Implement `computeDaily()`:
  - [ ] Set timeframe=1D
  - [ ] Set indicator_type=SUPERTREND
  - [ ] Determine snapshot_close_time list (Phase 3)
  - [ ] For each snapshot_close_time:
      - [ ] count active assets
      - [ ] fetch signal_state rows at that close_time
      - [ ] compute snapshot (calculator)
      - [ ] upsert snapshot
- [ ] Ensure deterministic ordering (oldest → newest)
- [ ] Log summary at end

---

## Phase 8 — Scheduler (Optional)

- [ ] Add `@Scheduled` method calling `MarketBreadthService.computeDaily()`
- [ ] Externalize cron config
- [ ] Ensure exceptions are caught and logged (no app crash)

---

## Phase 9 — Integration Tests (Testcontainers)

- [ ] Seed active assets
- [ ] Seed `signal_state` rows for multiple finalized days (same close_time across assets)
- [ ] Run `computeDaily()` and assert snapshots created for expected days
- [ ] Verify counts + ratio + missing_count are correct
- [ ] Run again and assert idempotency (no duplicates; skipped path)
- [ ] Modify a `signal_state` row and rerun
- [ ] Assert WARNING + updated snapshot row

---

## Phase 10 — Manual Validation

- [ ] Seed small universe (e.g., 5 active assets)
- [ ] Ensure signal_state exists for last few finalized days
- [ ] Run `computeDaily()`
- [ ] Inspect DB snapshot rows:
  - [ ] counts add up correctly
  - [ ] bullish_ratio correct
  - [ ] missing_count correct
  - [ ] deterministic on rerun