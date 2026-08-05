## ADDED Requirements

### Requirement: Asset has a category
Every asset SHALL have an `assetClass` of exactly one of `CRYPTO`, `STOCK`, `ETF`, or `COMMODITY`. The field SHALL be required (never null) for both existing and newly created assets.

#### Scenario: Existing assets are backfilled
- **WHEN** the asset-class migration runs against a database containing pre-existing assets
- **THEN** every existing asset row has `assetClass` set to `CRYPTO`, since every asset onboarded so far is a Binance crypto pair

#### Scenario: New asset requires a class
- **WHEN** a new asset is inserted without specifying `assetClass`
- **THEN** the insert is rejected (or defaults to `CRYPTO`, per the storage default), never left null

### Requirement: Read APIs support filtering by asset class
`GET /api/v1/signals`, `GET /api/v1/assets/by-state`, `POST /api/v1/scan`, `GET /api/v1/summary`, `GET /api/v1/trend-alignment`, and `GET /api/v1/candles/coverage` SHALL accept an optional `assetClass` filter. When supplied, results SHALL be restricted to assets of that class. When omitted, results SHALL include assets of all classes, identical to current behavior.

#### Scenario: Filtering signals by class
- **WHEN** a client calls `GET /api/v1/signals?timeframe=D1&assetClass=CRYPTO`
- **THEN** the response contains only signals for assets whose `assetClass` is `CRYPTO`

#### Scenario: Omitting the filter preserves existing behavior
- **WHEN** a client calls `GET /api/v1/signals?timeframe=D1` without `assetClass`
- **THEN** the response contains signals for active assets of every class, exactly as it did before this change

#### Scenario: Filtering to a class with no matching assets
- **WHEN** a client filters by a class that currently has zero active assets (e.g. `STOCK`, before any stock assets are onboarded)
- **THEN** the response is an empty result list, not an error

### Requirement: Asset class is visible in per-asset API responses
Any API response representing an individual asset's signal or match (e.g. `SignalSummary`, scan results) SHALL include the asset's `assetClass`.

#### Scenario: Signal summary includes asset class
- **WHEN** `GET /api/v1/signals` returns a signal for a given asset
- **THEN** the JSON entry for that asset includes its `assetClass`
