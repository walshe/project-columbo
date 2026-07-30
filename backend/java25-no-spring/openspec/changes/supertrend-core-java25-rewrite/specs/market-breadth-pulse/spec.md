## ADDED Requirements

### Requirement: Per-run breadth snapshot
After signal-state detection completes for a timeframe, the system SHALL compute and persist a market-breadth snapshot counting active assets in `BULLISH`, `BEARISH`, and `UNKNOWN`/missing signal state, plus total active asset count and bullish ratio, keyed by `(timeframe, snapshot_close_time)`.

#### Scenario: Snapshot counts reflect current signal states
- **WHEN** a breadth snapshot is computed for a timeframe after signal-state detection
- **THEN** the snapshot's bullish/bearish/missing counts sum to the total active asset count, and bullish ratio = bullish count / (bullish count + bearish count) when that denominator is nonzero

### Requirement: Latest pulse retrieval
The system SHALL expose retrieval of the most recent breadth snapshot for a given timeframe.

#### Scenario: Latest snapshot returned
- **WHEN** the latest breadth snapshot is requested for a timeframe that has at least one snapshot
- **THEN** the system returns the snapshot with the most recent `snapshot_close_time` for that timeframe

#### Scenario: No snapshot exists yet
- **WHEN** the latest breadth snapshot is requested for a timeframe with no snapshots
- **THEN** the system returns no result (not an error)

### Requirement: Historical pulse retrieval
The system SHALL expose retrieval of breadth snapshots for a timeframe within an optional date range, ordered by snapshot close time.

#### Scenario: Range query returns matching snapshots
- **WHEN** historical snapshots are requested for a timeframe with a `from`/`to` date range
- **THEN** the system returns all snapshots for that timeframe with `snapshot_close_time` within the range, ordered oldest to newest
