## MODIFIED Requirements

### Requirement: Daily candle fetch from Binance
The system SHALL fetch daily OHLC candles per active asset from Binance's public klines API, routed to the venue (spot or futures) configured for that asset, using an incremental time window computed from each asset's last stored D1 close time (or the configured backfill-start date if no candles are stored yet) through the current UTC-midnight boundary. Spot and futures are separate Binance products with separate hosts and separate klines paths; an asset's venue determines both.

#### Scenario: Incremental fetch for an asset with existing candles
- **WHEN** ingestion runs for an asset that already has D1 candles stored
- **THEN** the system requests only candles with open time after the asset's last stored close time, up to the current UTC-midnight boundary, from that asset's configured venue

#### Scenario: Initial backfill for an asset with no candles
- **WHEN** ingestion runs for an asset with no D1 candles stored yet
- **THEN** the system requests candles starting from the configured backfill-start date, from that asset's configured venue

#### Scenario: A spot asset and a futures asset in the same run are fetched from different endpoints
- **WHEN** an ingestion run includes at least one `SPOT`-venue asset and at least one `FUTURES`-venue asset
- **THEN** the `SPOT` asset is fetched from the spot klines endpoint and the `FUTURES` asset is fetched from the futures klines endpoint, each independently

## ADDED Requirements

### Requirement: Per-asset venue routing
Every asset SHALL have a venue (`SPOT` or `FUTURES`) determining which Binance product its candles are fetched from. Venue SHALL be a property of the asset, independent of its asset class — an asset class does not uniquely determine venue.

#### Scenario: A crypto asset can be futures-only
- **WHEN** a `CRYPTO`-class asset is only listed on Binance futures, not spot
- **THEN** its venue is `FUTURES`, and ingestion fetches it from the futures endpoint despite its class being `CRYPTO`
