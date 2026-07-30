# backfill-coverage Specification

## Purpose
TBD - created by archiving change tighten-backfill-start-with-failsafe. Update Purpose after archive.
## Requirements
### Requirement: Backfill start is validated against enabled weekly indicators at startup
The application SHALL, at startup, verify that `app.ingestion.backfill-start` provides at least the minimum daily history required for the enabled weekly (W1) indicators to warm up. The minimum SHALL be expressed as a number of weekly candles required by the most demanding enabled W1 indicator (currently W1 SuperTrend, which needs approximately 20 weekly candles to stabilise its ATR) and converted to a calendar lookback. If `now − backfill-start` is less than that lookback, the application SHALL fail to start with an `IllegalStateException` whose message states the configured start, the computed minimum, and how to resolve it.

#### Scenario: Sufficient backfill start boots normally
- **WHEN** `backfill-start` is `2025-07-01` and the current date provides more than the required weekly-candle lookback
- **THEN** the application starts normally and no validation error is raised

#### Scenario: Insufficient backfill start blocks startup
- **WHEN** `backfill-start` is set so that fewer than the required number of weekly candles (~20 for W1 SuperTrend) would be available
- **THEN** the application fails to start with an `IllegalStateException` explaining the shortfall

#### Scenario: Missing backfill start blocks startup
- **WHEN** `backfill-start` is not configured
- **THEN** the application fails to start with a clear configuration error rather than defaulting silently

### Requirement: Required-coverage floor is documented as dependent on enabled indicators
The validator SHALL define the required weekly-candle minimum as a named constant with an accompanying comment stating that the value tracks the currently enabled W1 indicators, and that re-enabling Elder Impulse / Market Thermometer (W1 EMA-13 / MACD needing ~100 weekly candles ≈ 2 years) requires raising the constant and moving `backfill-start` earlier.

#### Scenario: Constant carries maintenance guidance
- **WHEN** a developer reads the validator's minimum-coverage constant
- **THEN** the accompanying comment explains it reflects only the enabled indicators and what to change if Elder-family indicators are reinstated

