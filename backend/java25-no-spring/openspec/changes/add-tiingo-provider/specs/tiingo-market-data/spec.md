## ADDED Requirements

### Requirement: Real-equity asset onboarding via Tiingo
The system SHALL support onboarding assets sourced from Tiingo as real, exchange-traded equities — distinct from the existing tokenized Binance STOCK/ETF assets. Each Tiingo-sourced asset SHALL be stored with `provider = TIINGO`, `asset_class = STOCK`, `venue = EXCHANGE`, and a populated human-readable `name`. Onboarding a Tiingo asset SHALL NOT deactivate, modify, or otherwise affect any existing tokenized Binance asset for the same underlying company — both SHALL be able to exist as independent, simultaneously active rows.

#### Scenario: A Tiingo asset and its tokenized Binance counterpart coexist
- **WHEN** a Tiingo-sourced asset is onboarded for a company that already has an active tokenized Binance asset (e.g. real `AAPL` alongside the existing tokenized `AAPLUSDT`)
- **THEN** both rows remain active and independently ingested, with no deduplication or linkage between them

#### Scenario: A Tiingo asset is onboarded with a populated display name
- **WHEN** a Tiingo-sourced asset is inserted
- **THEN** its `name` column is populated with the company's real display name, not left null

### Requirement: Tiingo daily candle retrieval uses split/dividend-adjusted prices
The system SHALL fetch daily OHLC candles for Tiingo-sourced assets using Tiingo's split/dividend-adjusted price fields (`adjOpen`/`adjHigh`/`adjLow`/`adjClose`/`adjVolume`), not the raw unadjusted fields, so a stock split or dividend event does not appear as a discontinuous price jump in the stored candle series.

#### Scenario: A split event does not create a false price gap
- **WHEN** a Tiingo-sourced asset's underlying company undergoes a stock split during the ingested date range
- **THEN** the candles persisted for that asset reflect Tiingo's split-adjusted values, not the raw pre-adjustment prices

### Requirement: Tiingo API authentication
The system SHALL authenticate all requests to Tiingo's API using an API key sourced from the `TIINGO_API_KEY` environment variable, consistent with how other provider and ingestion configuration is sourced from the environment in this system.

#### Scenario: Missing API key
- **WHEN** `TIINGO_API_KEY` is not configured in the environment
- **THEN** the system fails to construct the Tiingo client at startup rather than attempting unauthenticated requests
