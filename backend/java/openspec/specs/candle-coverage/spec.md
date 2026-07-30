# candle-coverage Specification

## Purpose
TBD - created by archiving change candle-coverage-endpoint. Update Purpose after archive.
## Requirements
### Requirement: Candle coverage endpoint reports per-timeframe status
The system SHALL expose `GET /api/v1/candles/coverage` returning, for each supported timeframe (D1, W1), the earliest stored candle close time, the latest stored candle close time, the expected latest finalized close time as of now, an up-to-date flag, and the number of distinct assets with at least one candle in that timeframe.

#### Scenario: Coverage is reported for every timeframe
- **WHEN** a client requests `GET /api/v1/candles/coverage`
- **THEN** the response contains an entry for each timeframe (D1 and W1) with `earliest`, `latest`, `expectedLatest`, `upToDate`, and `assetCount` fields

#### Scenario: Empty timeframe is represented, not omitted
- **WHEN** a timeframe has no stored candles
- **THEN** its entry is present with null `earliest`/`latest`, `assetCount` of 0, and `upToDate` false

### Requirement: Up-to-date flag reflects finalized data
For each timeframe, `expectedLatest` SHALL be the start of the most recent finalized period as of now (i.e. excluding the still-forming current period), derived from the same finalized boundary the ingestion pipeline uses. `upToDate` SHALL be true when the latest stored candle close time is at or after `expectedLatest`, and false otherwise (including when there are no stored candles). Using the period start as the threshold avoids fragile equality against the exact stored close time.

#### Scenario: Current data is flagged up to date
- **WHEN** the latest stored D1 candle close time equals the most recent finalized daily close
- **THEN** `upToDate` for D1 is true

#### Scenario: Stale data is flagged not up to date
- **WHEN** the latest stored D1 candle is older than the most recent finalized daily close
- **THEN** `upToDate` for D1 is false and `latest` is earlier than `expectedLatest`

