## ADDED Requirements

### Requirement: Daily candle fetch from Binance
The system SHALL fetch daily OHLC candles per active asset from Binance's public klines API, using an incremental time window computed from each asset's last stored D1 close time (or the configured backfill-start date if no candles are stored yet) through the current UTC-midnight boundary.

#### Scenario: Incremental fetch for an asset with existing candles
- **WHEN** ingestion runs for an asset that already has D1 candles stored
- **THEN** the system requests only candles with open time after the asset's last stored close time, up to the current UTC-midnight boundary

#### Scenario: Initial backfill for an asset with no candles
- **WHEN** ingestion runs for an asset with no D1 candles stored yet
- **THEN** the system requests candles starting from the configured backfill-start date

### Requirement: Idempotent candle persistence
The system SHALL persist candles keyed uniquely by `(asset, timeframe, close_time)`. Re-ingesting a candle that already exists with identical OHLC values SHALL be a no-op; re-ingesting with different values SHALL update the stored row and log a warning identifying the revision — the system SHALL never insert a duplicate row for the same key.

#### Scenario: Re-ingesting identical data is a no-op
- **WHEN** a candle is ingested whose `(asset, timeframe, close_time)` already exists with identical OHLC values
- **THEN** no database write occurs and no warning is logged

#### Scenario: Re-ingesting revised data updates and warns
- **WHEN** a candle is ingested whose `(asset, timeframe, close_time)` already exists but with different OHLC values
- **THEN** the stored row is updated to the new values and a warning is logged identifying the asset, timeframe, and close time

### Requirement: Per-asset error isolation
A provider error for one asset SHALL NOT abort ingestion for other assets in the same run. The system SHALL record an error count and a truncated sample of errors on the run record, and SHALL continue processing remaining assets.

#### Scenario: One asset's provider error does not block others
- **WHEN** Binance returns an error for one asset during a run covering multiple assets
- **THEN** ingestion continues for the remaining assets, and the failed asset is counted in the run's error count with its error captured in the error sample

### Requirement: Invalid-symbol assets are deactivated
When Binance responds with an invalid-symbol error for an asset, the system SHALL mark that asset inactive so future runs skip it, rather than repeatedly failing on it.

#### Scenario: Asset deactivated after invalid-symbol response
- **WHEN** Binance responds with its invalid-symbol error code for an asset's configured provider ID
- **THEN** the system marks that asset inactive and does not include it in subsequent ingestion runs

### Requirement: D1-to-W1 rollup
The system SHALL derive weekly (W1) candles from finalized D1 candles by grouping into Monday-start weeks, emitting a W1 candle only when exactly 7 source D1 candles are present for that week and the last of those 7 is finalized.

#### Scenario: Complete week produces a W1 candle
- **WHEN** exactly 7 finalized D1 candles exist for a Monday-start week for an asset
- **THEN** the system emits one W1 candle for that week (open = first day's open, close = last day's close, high = week max, low = week min, volume = week sum)

#### Scenario: Incomplete week produces no W1 candle
- **WHEN** fewer than 7 D1 candles exist for a given week, or the 7th candle is not yet finalized
- **THEN** the system does not emit a W1 candle for that week

### Requirement: Backfill window sufficient for W1 warm-up
The system SHALL validate at startup that the configured backfill-start date provides at least 20 weekly candles (147 days) of history, and SHALL fail to start if it does not — since W1 SuperTrend's ATR(10) requires that much warm-up history on rolled-up weekly candles.

#### Scenario: Startup fails with insufficient backfill window
- **WHEN** the configured backfill-start date is less than 147 days before the current date
- **THEN** the system fails to start with an explicit error identifying the shortfall
