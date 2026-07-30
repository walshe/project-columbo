# signal-sort Specification

## Purpose
TBD - created by archiving change sort-signals-by-pct-change. Update Purpose after archive.
## Requirements
### Requirement: Signals can be ordered by percentage change since flip
The `/api/v1/signals` endpoint SHALL accept `sort=PCT_CHANGE_ASC` and `sort=PCT_CHANGE_DESC`, ordering results by each signal's `pctChangeSinceFlip`. `PCT_CHANGE_DESC` SHALL order from the largest (most positive) change to the smallest, and `PCT_CHANGE_ASC` from the smallest (most negative) to the largest. The sort SHALL be trend-relative by convention: the caller chooses the direction — `DESC` for a bullish list to surface the strongest gains, `ASC` for a bearish list to surface the strongest declines. The service SHALL NOT infer the direction from the trend-state filter.

#### Scenario: Descending orders largest gain first
- **WHEN** three bullish signals have `pctChangeSinceFlip` of +30, +10, and -10 and `sort=PCT_CHANGE_DESC`
- **THEN** they are returned in the order +30, +10, -10

#### Scenario: Ascending orders largest drop first
- **WHEN** three bearish signals have `pctChangeSinceFlip` of -30, -10, and +10 and `sort=PCT_CHANGE_ASC`
- **THEN** they are returned in the order -30, -10, +10

### Requirement: Signals without a percentage change sort last
When ordering by `PCT_CHANGE_ASC` or `PCT_CHANGE_DESC`, signals whose `pctChangeSinceFlip` is `null` (no recorded flip, or no candle found at the flip time) SHALL be placed after all signals that have a value, in both sort directions.

#### Scenario: Null percentage change sorts last on descending
- **WHEN** signals with `pctChangeSinceFlip` of +5, -5, and `null` are sorted with `sort=PCT_CHANGE_DESC`
- **THEN** the order is +5, -5, `null`

#### Scenario: Null percentage change sorts last on ascending
- **WHEN** signals with `pctChangeSinceFlip` of +5, -5, and `null` are sorted with `sort=PCT_CHANGE_ASC`
- **THEN** the order is -5, +5, `null`

### Requirement: Percentage-change sort is limited to the signals endpoint
The percentage-change sort options SHALL apply only to the `/api/v1/signals` endpoint. The `/api/v1/summary` and `/api/v1/summary/trend-alignment` reports SHALL retain their existing ordering and SHALL NOT be affected by this change.

#### Scenario: Summary report ordering is unchanged
- **WHEN** the `/api/v1/summary` report is generated
- **THEN** its signal sections remain ordered by flip recency as before, with no percentage-change ordering applied

