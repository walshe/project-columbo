## ADDED Requirements

### Requirement: Manual pipeline trigger
`POST /api/v1/internal/ingestion/run` SHALL accept an optional provider (defaulting to Binance) and optional timeframe (defaulting to D1), start a pipeline run via the same orchestration path used by the scheduled trigger, and respond with 202 Accepted plus a run ID and initial status.

#### Scenario: Trigger with defaults
- **WHEN** `POST /api/v1/internal/ingestion/run` is called with an empty body
- **THEN** the system starts a run for the default provider and D1 timeframe, returning 202 with a run ID and `RUNNING` status

#### Scenario: Trigger rejected while a run is already in progress
- **WHEN** `POST /api/v1/internal/ingestion/run` is called for a provider+timeframe that already has a `RUNNING` run
- **THEN** the response is 409 Conflict and no new run is started
