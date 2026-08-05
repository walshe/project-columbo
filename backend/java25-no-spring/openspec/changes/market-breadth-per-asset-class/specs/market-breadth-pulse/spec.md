## MODIFIED Requirements

### Requirement: Per-run breadth snapshot
After signal-state detection completes for a timeframe, the system SHALL compute and persist one market-breadth snapshot per asset class present among active assets, plus one combined snapshot across every active asset regardless of class, each counting `BULLISH`, `BEARISH`, and `UNKNOWN`/missing signal state, plus total asset count and bullish ratio for its scope. Snapshots SHALL be keyed by `(timeframe, snapshot_close_time, asset_class)`, where `asset_class` is `NULL` for the combined snapshot and a specific class for a per-class snapshot. A class with zero active assets SHALL NOT produce a snapshot for that run.

#### Scenario: Snapshot counts reflect current signal states
- **WHEN** a breadth snapshot is computed for a timeframe and class (or combined) after signal-state detection
- **THEN** the snapshot's bullish/bearish/missing counts sum to the total asset count for that scope, and bullish ratio = bullish count / (bullish count + bearish count) when that denominator is nonzero

#### Scenario: Per-class and combined snapshots coexist for the same run
- **WHEN** a pipeline run computes breadth for a timeframe with active assets in more than one class
- **THEN** one snapshot exists for each class with at least one active asset, plus exactly one combined snapshot spanning all of them, all sharing the same `snapshot_close_time`

#### Scenario: A class with no active assets produces no snapshot
- **WHEN** breadth is computed for a timeframe and a class that currently has zero active assets
- **THEN** no snapshot row is created or updated for that class on this run

### Requirement: Latest pulse retrieval
The system SHALL expose retrieval of the most recent breadth snapshot for a given timeframe and an optional asset class filter. Omitting the filter SHALL return the combined snapshot.

#### Scenario: Latest snapshot returned
- **WHEN** the latest breadth snapshot is requested for a timeframe and class (or combined) that has at least one snapshot
- **THEN** the system returns the snapshot with the most recent `snapshot_close_time` for that timeframe and scope

#### Scenario: No snapshot exists yet
- **WHEN** the latest breadth snapshot is requested for a timeframe/class combination with no snapshots
- **THEN** the system returns no result (not an error)

### Requirement: Historical pulse retrieval
The system SHALL expose retrieval of breadth snapshots for a timeframe and an optional asset class filter within an optional date range, ordered by snapshot close time. Omitting the class filter SHALL return only combined snapshots, not a mix of every class's rows.

#### Scenario: Range query returns matching snapshots
- **WHEN** historical snapshots are requested for a timeframe and class (or combined) with a `from`/`to` date range
- **THEN** the system returns all snapshots for that timeframe and scope with `snapshot_close_time` within the range, ordered oldest to newest
