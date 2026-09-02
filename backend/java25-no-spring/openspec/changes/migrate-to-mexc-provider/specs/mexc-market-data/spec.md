## ADDED Requirements

### Requirement: Unauthenticated MEXC daily candle retrieval
The system SHALL fetch daily OHLC candles for MEXC-sourced assets from `GET /api/v3/klines` without an API key, using the same `symbol`/`interval=1d`/`startTime`/`endTime` query parameters and the same 8-field response row shape (open time, open, high, low, close, volume, close time, quote volume) already used for Binance's spot klines.

#### Scenario: A MEXC asset is fetched without any configured credential
- **WHEN** ingestion runs for a `MEXC`-venue asset
- **THEN** the request to `/api/v3/klines` succeeds with no API key or authentication header, and the response rows are parsed into `Candle` values using the same field order as Binance's klines parsing

### Requirement: Asset classification via exchangeInfo concept tags
The system SHALL classify a MEXC symbol as `STOCK` or `ETF` only when its `exchangeInfo` entry's `conceptPlates` array contains `"mc-trade-zone-xStocks"`; within that set, a `fullName` containing "ETF" (case-insensitive) classifies it as `ETF`, otherwise `STOCK`. Every other USDT-quoted, tradeable symbol SHALL classify as `CRYPTO`. The `baseAsset` symbol suffix (e.g. ending in "ON") SHALL NOT be used as a classification signal.

#### Scenario: A tokenized-equity symbol classifies correctly
- **WHEN** a symbol's `conceptPlates` contains `"mc-trade-zone-xStocks"` and its `fullName` does not contain "ETF"
- **THEN** it classifies as `STOCK`

#### Scenario: A tokenized ETF symbol classifies correctly
- **WHEN** a symbol's `conceptPlates` contains `"mc-trade-zone-xStocks"` and its `fullName` contains "ETF"
- **THEN** it classifies as `ETF`

#### Scenario: A crypto symbol whose base asset happens to end in a stock-like suffix is not misclassified
- **WHEN** a symbol's `baseAsset` ends in "ON" (or any other suffix associated with tokenized equities) but its `conceptPlates` does not contain `"mc-trade-zone-xStocks"`
- **THEN** it classifies as `CRYPTO`, not `STOCK` or `ETF`

### Requirement: No provider-level rate limiting for MEXC
The system SHALL NOT apply any throttle, minimum-interval delay, or retry-budget limiter specific to `MexcMarketDataProvider`, since MEXC's public market-data rate limit (500 requests/10 seconds, weight 1 per klines call) is not a binding constraint at this system's asset-count scale.

#### Scenario: A full ingestion sweep completes without provider-specific pacing
- **WHEN** an ingestion run fetches candles for every active `MEXC`-venue asset
- **THEN** `MexcMarketDataProvider` issues requests with no added delay beyond `CandleIngestionService`'s existing uniform per-asset pacing
