## MODIFIED Requirements

### Requirement: Daily candle fetch from Binance
The system SHALL fetch daily OHLC candles per active asset from that asset's configured provider (Binance's public klines API, Tiingo's daily prices API, or MEXC's public klines API), routed to the venue (`SPOT`, `FUTURES`, `EXCHANGE`, or `MEXC`) configured for that asset, using an incremental time window computed from each asset's last stored D1 close time (or the configured backfill-start date if no candles are stored yet) through the current UTC-midnight boundary. Spot and futures are separate Binance products with separate hosts and separate klines paths; `EXCHANGE` routes to Tiingo; `MEXC` routes to MEXC's own klines endpoint, covering CRYPTO, STOCK, and ETF asset classes through that single venue. An asset's venue determines both which provider and which endpoint its candles come from.

#### Scenario: Incremental fetch for an asset with existing candles
- **WHEN** ingestion runs for an asset that already has D1 candles stored
- **THEN** the system requests only candles with open time after the asset's last stored close time, up to the current UTC-midnight boundary, from that asset's configured venue

#### Scenario: Initial backfill for an asset with no candles
- **WHEN** ingestion runs for an asset with no D1 candles stored yet
- **THEN** the system requests candles starting from the configured backfill-start date, from that asset's configured venue

#### Scenario: A MEXC asset in the same run as a dormant Binance/Tiingo asset is fetched independently
- **WHEN** an ingestion run includes at least one active `MEXC`-venue asset, with no active `SPOT`/`FUTURES`/`EXCHANGE`-venue assets remaining
- **THEN** the `MEXC` asset is fetched from MEXC's klines endpoint, and the run completes without requiring a Binance or Tiingo client to be configured

### Requirement: Per-asset venue routing
Every asset SHALL have a venue (`SPOT`, `FUTURES`, `EXCHANGE`, or `MEXC`) determining which market data client its candles are fetched from. Venue SHALL be a property of the asset, independent of its asset class — an asset class does not uniquely determine venue. `MEXC` SHALL be used for assets sourced from MEXC's klines API, regardless of whether the asset's class is `CRYPTO`, `STOCK`, or `ETF`.

#### Scenario: A crypto asset can be futures-only
- **WHEN** a `CRYPTO`-class asset is only listed on Binance futures, not spot
- **THEN** its venue is `FUTURES`, and ingestion fetches it from the futures endpoint despite its class being `CRYPTO`

#### Scenario: A MEXC-sourced stock and a MEXC-sourced crypto asset share one venue
- **WHEN** a `STOCK`-class asset and a `CRYPTO`-class asset are both sourced from MEXC
- **THEN** both have venue `MEXC` and are both fetched via `MexcMarketDataProvider`, unlike Binance where asset class and venue can diverge (spot vs. futures) or Tiingo where `EXCHANGE` is reserved for real equities only

### Requirement: Binance and Tiingo assets can be deactivated without a required credential
The system SHALL NOT require `TIINGO_API_KEY` to be configured at startup once no active asset uses the `EXCHANGE` venue. When `TIINGO_API_KEY` is absent, the system SHALL start successfully without constructing a Tiingo client or an `EXCHANGE` entry in the venue-routing map.

#### Scenario: Startup succeeds with Tiingo fully dormant
- **WHEN** `TIINGO_API_KEY` is not set in the environment and no active asset has venue `EXCHANGE`
- **THEN** the application starts successfully and ingestion runs complete without attempting to construct or call a Tiingo client

#### Scenario: Startup still supports Tiingo when reactivated
- **WHEN** `TIINGO_API_KEY` is set in the environment
- **THEN** a Tiingo client is constructed and wired to the `EXCHANGE` venue exactly as before, so reactivating Tiingo-provider assets requires no code change — only setting the key and flipping `active` back to true
