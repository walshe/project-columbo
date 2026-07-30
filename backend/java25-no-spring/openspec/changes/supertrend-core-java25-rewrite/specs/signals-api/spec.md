## ADDED Requirements

### Requirement: List signals by timeframe
`GET /api/v1/signals` SHALL return the current signal state (symbol, trend state, last flip time, days since flip, 7-day average volume, TradingView chart URL, percentage change since flip) for every active asset in a given timeframe, accepting a required `timeframe` parameter, an optional `state` filter, an optional `sort` parameter, and an optional `requireFresh` flag.

#### Scenario: Unfiltered list for a timeframe
- **WHEN** `GET /api/v1/signals?timeframe=D1` is called with no other parameters
- **THEN** the response contains one entry per active asset's current D1 signal state, plus freshness metadata

#### Scenario: Filtered by trend state
- **WHEN** `GET /api/v1/signals?timeframe=D1&state=BULLISH` is called
- **THEN** the response contains only assets currently in `BULLISH` state

#### Scenario: Stale data rejected when requireFresh is set
- **WHEN** `GET /api/v1/signals?timeframe=D1&requireFresh=true` is called and D1 data is stale beyond the grace window
- **THEN** the response is 503 with a stale-data error body and a `Retry-After` header

### Requirement: Sortable signal list
The system SHALL support sorting the signals list by asset symbol ascending, last-flip time ascending or descending, trend state ascending, liquidity (7-day average volume) descending, or percentage-change-since-flip ascending or descending.

#### Scenario: Sort by percentage change descending
- **WHEN** `GET /api/v1/signals?timeframe=D1&sort=PCT_CHANGE_DESC` is called
- **THEN** the returned signals are ordered by percentage change since flip, highest first

### Requirement: Assets filtered by required state
`GET /api/v1/assets/by-state` SHALL return the same per-asset signal information as `/signals`, but requires a `state` parameter and does not support sorting or freshness gating.

#### Scenario: Required state parameter
- **WHEN** `GET /api/v1/assets/by-state?timeframe=D1&state=BEARISH` is called
- **THEN** the response contains only assets currently in `BEARISH` state for D1

#### Scenario: Missing state parameter rejected
- **WHEN** `GET /api/v1/assets/by-state?timeframe=D1` is called without a `state` parameter
- **THEN** the response is 400
