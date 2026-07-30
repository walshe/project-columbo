## ADDED Requirements

### Requirement: Per-timeframe candle coverage report
`GET /api/v1/candles/coverage` SHALL return, per timeframe (D1 and W1), the earliest stored candle date, the latest stored candle date, the expected-latest date per the freshness boundary rules, whether that timeframe is up to date, and the count of active assets with at least one candle.

#### Scenario: Coverage reflects stored data
- **WHEN** `GET /api/v1/candles/coverage` is called
- **THEN** the response includes an entry for D1 and an entry for W1, each with earliest/latest/expectedLatest dates, an up-to-date flag, and an asset count
