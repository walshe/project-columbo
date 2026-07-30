## ADDED Requirements

### Requirement: SuperTrend-only summary report
`GET /api/v1/summary` SHALL return the current SuperTrend market pulse for a timeframe plus lists of assets currently bullish and currently bearish on SuperTrend for that timeframe. The response SHALL NOT include any RSI-derived fields — this is a deliberate behavior change from the prior implementation, which combined SuperTrend state with RSI-crossed-threshold conditions via an internal scan; that combination is out of scope here.

#### Scenario: Summary contains only SuperTrend-derived data
- **WHEN** `GET /api/v1/summary?timeframe=D1` is called
- **THEN** the response includes the D1 market pulse and bullish/bearish SuperTrend asset lists, with no RSI-related fields present

### Requirement: Multiple output formats
The endpoint SHALL support `format=JSON` (default), `format=MARKDOWN`, and `format=WATCHLIST`, consistent with the format conventions used by the trend-alignment endpoint.

#### Scenario: Watchlist format requested
- **WHEN** `GET /api/v1/summary?timeframe=D1&format=WATCHLIST` is called
- **THEN** the response body is plain text with section headers followed by TradingView-importable symbol lines

### Requirement: Freshness metadata included
The response SHALL include the last successful ingestion timestamp and the latest candle date for the requested timeframe.

#### Scenario: Freshness fields present
- **WHEN** the endpoint is called after a successful pipeline run
- **THEN** the response includes non-null `lastIngestionAt` and `candlesThrough` fields
