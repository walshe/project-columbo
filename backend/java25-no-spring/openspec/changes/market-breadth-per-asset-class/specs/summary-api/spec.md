## MODIFIED Requirements

### Requirement: SuperTrend-only summary report
`GET /api/v1/summary` SHALL return the current SuperTrend market pulse for a timeframe plus lists of assets currently bullish and currently bearish on SuperTrend for that timeframe. When an `assetClass` filter is supplied, both the bullish/bearish asset lists and the market pulse SHALL be scoped to that class; omitting it SHALL combine every class in both. The response SHALL NOT include any RSI-derived fields — this is a deliberate behavior change from the prior implementation, which combined SuperTrend state with RSI-crossed-threshold conditions via an internal scan; that combination is out of scope here.

#### Scenario: Summary contains only SuperTrend-derived data
- **WHEN** `GET /api/v1/summary?timeframe=D1` is called
- **THEN** the response includes the D1 market pulse and bullish/bearish SuperTrend asset lists, with no RSI-related fields present

#### Scenario: Pulse respects the asset class filter
- **WHEN** `GET /api/v1/summary?timeframe=D1&assetClass=STOCK` is called
- **THEN** the response's `pulse` field reflects only stock assets, consistent with the bullish/bearish signal lists in the same response

#### Scenario: Omitting the asset class filter combines every class
- **WHEN** `GET /api/v1/summary?timeframe=D1` is called without `assetClass`
- **THEN** the response's `pulse` field reflects every active asset regardless of class, matching the unfiltered signal lists
