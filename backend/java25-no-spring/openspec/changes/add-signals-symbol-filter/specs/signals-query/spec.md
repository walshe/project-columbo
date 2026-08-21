## ADDED Requirements

### Requirement: List latest signal state per active asset
The system SHALL expose `GET /api/v1/signals`, returning the latest SuperTrend `TrendState` for every active asset on a given `Timeframe`, enriched with 7-day average volume, percentage change since the asset's last flip, a TradingView deep link, and the asset's class.

#### Scenario: No filters beyond timeframe
- **WHEN** `GET /api/v1/signals?timeframe=D1` is called with no other query params
- **THEN** the response contains one entry per currently-active asset with a recorded D1 signal state, sorted by symbol ascending (the default sort)

### Requirement: Optional state filter
The system SHALL accept an optional `state` query param (`TrendState`: `BULLISH`/`BEARISH`) that narrows the response to assets currently in that state on the requested timeframe.

#### Scenario: Filter by state
- **WHEN** `GET /api/v1/signals?timeframe=D1&state=BULLISH` is called
- **THEN** only assets whose latest D1 `TrendState` is `BULLISH` appear in the response

### Requirement: Optional asset class filter
The system SHALL accept an optional `assetClass` query param (`AssetClass`) that narrows the response to assets in that category.

#### Scenario: Filter by asset class
- **WHEN** `GET /api/v1/signals?timeframe=D1&assetClass=CRYPTO` is called
- **THEN** only crypto assets appear in the response

### Requirement: Optional symbol filter
The system SHALL accept an optional `symbols` query param - a comma-separated list of exact, case-sensitive symbol strings (e.g. `symbols=BTCUSDT,ETHUSDT`) - that narrows the response to only the requested symbol(s). This filter SHALL compose with `state`, `assetClass`, and `sort`. A symbol in the list that does not match any active asset SHALL be silently omitted from the response rather than producing an error.

#### Scenario: Single symbol lookup
- **WHEN** `GET /api/v1/signals?timeframe=D1&symbols=BTCUSDT` is called
- **THEN** the response contains at most one entry, for `BTCUSDT`, if it is an active asset with a recorded D1 signal state

#### Scenario: Multiple symbol lookup
- **WHEN** `GET /api/v1/signals?timeframe=D1&symbols=BTCUSDT,ETHUSDT` is called
- **THEN** the response contains at most two entries, one per requested symbol that matches an active asset

#### Scenario: Unknown or inactive symbol
- **WHEN** `GET /api/v1/signals?timeframe=D1&symbols=NOTASYMBOL` is called
- **THEN** the response contains zero entries and no error is raised

#### Scenario: Symbol filter combined with state filter
- **WHEN** `GET /api/v1/signals?timeframe=D1&symbols=BTCUSDT,ETHUSDT&state=BULLISH` is called and only `BTCUSDT` is currently `BULLISH`
- **THEN** the response contains only the `BTCUSDT` entry

### Requirement: Optional sort
The system SHALL accept an optional `sort` query param (`SignalSort`), defaulting to `ASSET_ASC` when omitted, controlling the ordering of the returned entries.

#### Scenario: Default sort
- **WHEN** `GET /api/v1/signals?timeframe=D1` is called with no `sort` param
- **THEN** entries are ordered by symbol ascending

### Requirement: Optional freshness gating
The system SHALL accept an optional `requireFresh` boolean query param. When `true` and the requested timeframe's data is stale beyond the configured grace window, the system SHALL respond `503` instead of returning data.

#### Scenario: Stale data with requireFresh=true
- **WHEN** `GET /api/v1/signals?timeframe=D1&requireFresh=true` is called and D1 data is stale beyond the grace window
- **THEN** the system responds `503` and does not return signal data

#### Scenario: Stale data with requireFresh omitted or false
- **WHEN** `GET /api/v1/signals?timeframe=D1` is called (or `requireFresh=false`) and D1 data is stale beyond the grace window
- **THEN** the system still returns the (stale) signal data, with staleness indicated in the response's freshness metadata

### Requirement: List active assets by state, unfiltered by symbol
The system SHALL separately expose `GET /api/v1/assets/by-state`, requiring both `timeframe` and `state`, and accepting an optional `assetClass` filter. This endpoint SHALL NOT accept a `symbols` filter - it is a browse-by-state endpoint, not a symbol lookup, and is not freshness-gated.

#### Scenario: Browse all bearish crypto assets
- **WHEN** `GET /api/v1/assets/by-state?timeframe=W1&state=BEARISH&assetClass=CRYPTO` is called
- **THEN** every active crypto asset currently `BEARISH` on W1 is returned, with no symbol-based narrowing available
