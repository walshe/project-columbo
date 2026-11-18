## MODIFIED Requirements

### Requirement: List latest signal state per active asset
The system SHALL expose `GET /api/v1/signals`, returning the latest trend `TrendState` for every active asset on a given `Timeframe`, enriched with 7-day average volume, percentage change since the asset's last flip, the most recent candle close price (`latestClose`), a TradingView deep link, and the asset's class.

#### Scenario: No filters beyond timeframe
- **WHEN** `GET /api/v1/signals?timeframe=D1` is called with no other query params
- **THEN** the response contains one entry per currently-active asset with a recorded D1 signal state, sorted by symbol ascending (the default sort), and each entry includes a `latestClose` field containing the asset's most recent D1 candle close as a decimal string

#### Scenario: latestClose is null when no candle exists
- **WHEN** `GET /api/v1/signals?timeframe=D1` is called and an active asset has no stored D1 candles
- **THEN** that asset's entry has `latestClose: null`
