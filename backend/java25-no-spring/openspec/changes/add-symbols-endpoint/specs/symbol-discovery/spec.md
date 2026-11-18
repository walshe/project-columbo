## ADDED Requirements

### Requirement: List active asset symbols
The system SHALL expose `GET /api/v1/symbols`, returning a JSON array of every currently-active asset's symbol string, sorted ascending, with no duplicates. No query parameters are accepted.

#### Scenario: Symbols are returned sorted ascending
- **WHEN** `GET /api/v1/symbols` is called
- **THEN** the response is a JSON array of symbol strings sorted alphabetically ascending (e.g. `["BTCUSDT", "ETHUSDT", ...]`)

#### Scenario: Response reflects only active assets
- **WHEN** an asset exists in the database but is marked inactive
- **THEN** its symbol does NOT appear in the `/api/v1/symbols` response

#### Scenario: Empty list when no active assets exist
- **WHEN** no assets are currently active
- **THEN** the response is an empty JSON array `[]`
