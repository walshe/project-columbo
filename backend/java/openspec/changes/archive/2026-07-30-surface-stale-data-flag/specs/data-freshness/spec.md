## ADDED Requirements

### Requirement: Read responses expose a data-staleness flag
The `/api/v1/signals`, `/api/v1/summary`, and `/api/v1/summary/trend-alignment` responses SHALL include a boolean `stale` field indicating whether the data backing the response lacks the most recent finalized candle for the relevant timeframe. `stale` SHALL be the logical negation of the shared up-to-date determination for that timeframe (the same definition used by the candle coverage endpoint). For a single-timeframe endpoint the relevant timeframe is the one requested; for the cross-timeframe trend-alignment report it is D1.

#### Scenario: Fresh data is not flagged stale
- **WHEN** the latest stored candle for the requested timeframe has reached the most recent finalized period
- **THEN** the response `stale` field is false

#### Scenario: Missing latest candle is flagged stale
- **WHEN** the latest stored candle for the requested timeframe is behind the most recent finalized period
- **THEN** the response `stale` field is true and the response body is still returned

### Requirement: Staleness determination is shared with coverage reporting
The staleness flag SHALL be derived from the same per-timeframe up-to-date determination that the candle coverage endpoint reports, so the two never disagree. There SHALL be a single component that computes, for a timeframe as of now, the expected most-recent finalized period and whether stored data has reached it.

#### Scenario: Flag agrees with coverage endpoint
- **WHEN** the coverage endpoint reports `upToDate` false for a timeframe
- **THEN** a read response for that timeframe reports `stale` true for the same instant

### Requirement: Callers can require fresh data
The read endpoints SHALL accept an optional `requireFresh` query parameter defaulting to false. When `requireFresh` is true and the relevant timeframe's data is stale beyond a grace period that accommodates the normal post-boundary ingestion window, the endpoint SHALL respond with HTTP 503 and an explanatory body instead of returning stale data. When `requireFresh` is false or omitted, the endpoint SHALL return data normally with the `stale` flag set.

#### Scenario: Strict caller is rejected on stale data
- **WHEN** `requireFresh=true` and the relevant timeframe is stale beyond the grace period
- **THEN** the endpoint responds with 503 and does not return the normal payload

#### Scenario: Strict caller within grace window is served
- **WHEN** `requireFresh=true` and the data is only missing the just-finalized candle within the grace window after the period boundary
- **THEN** the endpoint returns data normally rather than 503

#### Scenario: Default callers are unaffected
- **WHEN** `requireFresh` is omitted and the data is stale
- **THEN** the endpoint returns the normal payload with `stale` true
