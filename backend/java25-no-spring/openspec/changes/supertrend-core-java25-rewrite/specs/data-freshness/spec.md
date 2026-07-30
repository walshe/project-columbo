## ADDED Requirements

### Requirement: Expected-latest-candle boundary
The system SHALL compute the expected latest finalized close time for a timeframe as UTC-midnight-today minus one day for D1, or minus seven days for W1, and SHALL compare it against the actual latest stored close time to determine whether that timeframe's data is up to date.

#### Scenario: D1 up to date
- **WHEN** the latest stored D1 close time equals UTC-midnight-today minus one day
- **THEN** D1 is considered up to date

#### Scenario: W1 stale
- **WHEN** the latest stored W1 close time is older than UTC-midnight-today minus seven days
- **THEN** W1 is considered not up to date

### Requirement: Grace window before treating requests as stale
The system SHALL apply a 6-hour grace window after the expected-latest-candle boundary before treating a `requireFresh` request as truly stale — i.e. a timeframe that is technically behind the expected boundary but within the grace window SHALL NOT trigger a stale rejection.

#### Scenario: Within grace window
- **WHEN** a timeframe is behind its expected-latest boundary by less than 6 hours and a request has `requireFresh=true`
- **THEN** the request is served normally, not rejected as stale

#### Scenario: Beyond grace window
- **WHEN** a timeframe is behind its expected-latest boundary by more than 6 hours and a request has `requireFresh=true`
- **THEN** the request is rejected with a 503 response including a `Retry-After` header

### Requirement: Freshness metadata on responses
Every read API response in scope SHALL include the timestamp of the last successful ingestion and the date of the most recent stored candle, so clients can independently judge data recency.

#### Scenario: Freshness fields present after a successful run
- **WHEN** any in-scope read endpoint is called after at least one successful ingestion run
- **THEN** the response includes a non-null last-ingestion timestamp and a non-null latest-candle date

#### Scenario: Freshness fields null before any ingestion
- **WHEN** any in-scope read endpoint is called before any ingestion run has ever succeeded
- **THEN** the response includes null last-ingestion timestamp and latest-candle date fields, rather than an error
