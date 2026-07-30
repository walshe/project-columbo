## ADDED Requirements

### Requirement: SuperTrend-only condition matching
`POST /api/v1/scan` SHALL accept a request body of one or more conditions (timeframe, trend-state filter, max-days-since-flip filter) combined with an AND/OR operator, and return assets whose current SuperTrend signal state matches the combined condition, up to an optional result limit. Every condition SHALL implicitly target the `SUPERTREND` indicator — there is no indicator-type field to set, since SuperTrend is the only indicator in this system. This is a behavior change from the prior implementation, where conditions specified an indicator type (SUPERTREND or RSI) and could combine across indicators; that cross-indicator composition is out of scope here.

#### Scenario: Single condition match
- **WHEN** a scan request has one condition (`timeframe=D1`, `state=BULLISH`)
- **THEN** the response contains every active asset currently bullish on D1 SuperTrend

#### Scenario: AND-combined conditions across timeframes
- **WHEN** a scan request combines a D1-bullish condition and a W1-bullish condition with AND
- **THEN** the response contains only assets matching both conditions simultaneously

#### Scenario: Days-since-flip filter
- **WHEN** a scan condition includes `maxDaysSinceFlip=10`
- **THEN** only assets whose most recent flip occurred within the last 10 days match that condition

### Requirement: Result limit honored
The system SHALL cap the number of returned matches at the request's `limit` field when provided.

#### Scenario: Limit truncates results
- **WHEN** a scan request sets `limit=5` and more than 5 assets match
- **THEN** the response contains at most 5 matched assets

### Requirement: Standalone capability, not an internal dependency of other read APIs in this system
The `summary-api` and `trend-alignment-api` capabilities SHALL compute their SuperTrend-only lists directly against signal state and SHALL NOT call `scan-api` internally — unlike the prior implementation, where the generic `/summary` endpoint called the scan engine internally to combine SuperTrend state with RSI conditions. `scan-api` SHALL be exposed solely as a standalone public endpoint for composable SuperTrend condition queries.

#### Scenario: Summary endpoint does not depend on scan
- **WHEN** `/api/v1/summary` is called
- **THEN** its response is produced without invoking the scan-matching capability
