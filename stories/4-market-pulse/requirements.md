# Story 004 — Market Breadth Snapshot Across Assets

## 1. Objective

Compute and persist daily market breadth snapshots across the tracked asset universe for a given:

- timeframe (1D)
- indicator_type (initially SUPERTREND)

Market breadth answers:

- How many assets are bullish vs bearish?
- What is the bullish ratio?
- What is the latest finalized close_time used for the snapshot?
- How many assets are missing signal_state for that day?

This story enables simple “market pulse” reporting without requiring a UI.

---

## 2. Scope

This story:

- Reads finalized rows from `signal_state`
- Aggregates **across assets** (breadth), not across indicators
- Produces **one snapshot** per:
    (timeframe, indicator_type, snapshot_close_time)
- Persists the snapshot into `market_breadth_snapshot`

This story does NOT:

- Combine multiple indicators into a composite per-asset pulse
- Recompute indicators
- Recompute signal_state
- Implement trading logic
- Trigger orders, alerts, or notifications

This is strictly breadth aggregation.

---

## 3. Inputs

### 3.1 Source Table

Read from:

    signal_state

Filtering:

- timeframe = 1D
- indicator_type = 'SUPERTREND' (for Story 4)
- close_time < UTC midnight boundary (finalized rule)

---

## 4. Breadth Snapshot Definition

For each snapshot_close_time (finalized day), compute across all ACTIVE assets:

- bullish_count
- bearish_count
- missing_count
- total_assets
- bullish_ratio = bullish_count / total_assets

Definitions:

- total_assets = count(active assets)
- bullish_count = number of active assets with signal_state(trend_state=BULLISH) at snapshot_close_time
- bearish_count = number of active assets with signal_state(trend_state=BEARISH) at snapshot_close_time
- missing_count = total_assets - bullish_count - bearish_count

A snapshot is valid only for finalized days and must include missing_count.

---

## 5. Persistence Model

### 5.1 Table: market_breadth_snapshot

Create table:

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

Unique constraint:

    (timeframe, indicator_type, snapshot_close_time)

---

## 6. Finalized Rule

Snapshots must only be computed for:

    snapshot_close_time < current UTC day boundary

Never compute or store a snapshot for the current open day.

---

## 7. Determinism & Idempotency

Given identical input signal_state rows:

- Snapshots must be identical.
- Re-running must not create duplicates.

Use upsert semantics:

- If identical → skip
- If different → log WARNING → update

Exclude created_at from comparison.

Track:

- inserted_count
- updated_count
- skipped_count

---

## 8. Incremental Processing

Default mode: Incremental.

1. Determine latest stored snapshot_close_time for:
       timeframe + indicator_type

2. Determine latest finalized close_time available in signal_state for:
       timeframe + indicator_type

3. For each missing snapshot_close_time between them:
       compute and persist snapshot sequentially oldest → newest

Full rebuild mode supported.

---

## 9. Service Layer

Create:

    MarketBreadthService

Method:

    computeDaily()

Steps:

1. Determine which snapshot_close_time values to compute (incremental)
2. For each snapshot_close_time:
   - fetch active asset count
   - fetch signal_state rows for that close_time
   - compute counts + ratio + missing_count
   - persist via upsert
3. Log summary

---

## 10. Testing Requirements

### Unit Tests

- Given 3 assets:
    - 2 bullish, 1 bearish → ratio=0.666...
- Missing asset state increases missing_count
- Deterministic recomputation produces identical snapshot

### Integration Tests (Testcontainers)

- Seed assets + signal_state for multiple days
- Run computeDaily()
- Verify snapshots exist for expected days
- Run computeDaily() again
- Assert idempotency
- Modify a signal_state row
- Re-run and assert WARNING + updated snapshot

---

## 11. Out of Scope

- Per-asset composite pulse (multi-indicator voting)
- REST endpoints (Story 5)
- OpenClaw integration (Story 6)
- Indicator weighting / confidence scoring
- Alerts and notifications

---

## 12. Success Criteria

- Snapshots are created for finalized days present in signal_state
- bullish_count + bearish_count + missing_count = total_assets
- Re-running produces no duplicates
- Snapshots remain deterministic and reproducible