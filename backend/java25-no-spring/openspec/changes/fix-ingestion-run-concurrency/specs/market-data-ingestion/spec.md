## ADDED Requirements

### Requirement: Single-flight ingestion run lock is provider-agnostic
The system SHALL allow at most one ingestion pipeline run per timeframe to be in the `RUNNING` state at any time, independent of any provider concept. A request to start a run for a timeframe that already has a `RUNNING` run SHALL be rejected, regardless of how either run was triggered.

#### Scenario: A second trigger for the same timeframe is rejected while one is running
- **WHEN** an ingestion run for a timeframe is `RUNNING` and a new run is requested for that same timeframe
- **THEN** the new request is rejected with an already-running error, not accepted to run concurrently

#### Scenario: The ingestion trigger endpoint does not accept a provider parameter
- **WHEN** `POST /api/v1/internal/ingestion/run` is called
- **THEN** its request body accepts only an optional `timeframe` field; any provider-scoping of the triggered run is not possible, since a single run always processes every active asset regardless of provider

### Requirement: Candle and indicator upserts are atomic under concurrent writes
The system SHALL persist a candle or indicator result for a given `(asset, timeframe, close_time)` via a single atomic database operation, such that two concurrent writes for the same key converge to a consistent final state rather than one of them failing with a constraint-violation error.

#### Scenario: Two concurrent upserts for the same new key both succeed
- **WHEN** two concurrent writes attempt to persist a candle (or indicator result) for the same asset, timeframe, and close time that does not yet have a stored row
- **THEN** exactly one insert and one update occur (or one insert and one no-op if the values are identical), and neither write raises an unhandled constraint-violation error

### Requirement: A zero-candle fetch during an expected window is logged
The system SHALL log a warning when a market data provider is queried for an asset's candles during a window where new data was expected (i.e. the asset was not already fully up to date) but the provider returns zero candles, distinguishing this case from "already up to date" in the logs.

#### Scenario: A provider returns an empty but valid response
- **WHEN** ingestion fetches candles for an asset whose last stored close time is before the current fetch window's end, and the provider's response contains zero candles
- **THEN** a warning is logged identifying the asset, distinct from the debug-level "no new candles required" case for an asset that was already caught up
