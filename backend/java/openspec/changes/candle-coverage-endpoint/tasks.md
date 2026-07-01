## 1. Repository

- [x] 1.1 Add per-timeframe aggregate queries to `CandleRepository`: earliest close time `MIN(close_time)`, and distinct asset count `COUNT(DISTINCT asset_id)` (latest already exists via `findLatestCloseTimeForTimeframe`)

## 2. DTO

- [x] 2.1 Add a `CandleCoverageDto` (per-timeframe: `earliest`, `latest`, `expectedLatest`, `upToDate`, `assetCount`) and a response type keyed by timeframe

## 3. Service

- [x] 3.1 Add `CandleCoverageService` that, for each `Timeframe`, reads the aggregates, computes `expectedLatest` from the shared finalized-boundary logic (via `TimeProvider`), sets `upToDate = latest != null && latest >= expectedLatest`, and normalises native-query return types (reuse the `toOffsetDateTime` pattern)

## 4. Controller

- [x] 4.1 Add `CandleCoverageController` exposing `GET /api/v1/candles/coverage` delegating to the service

## 5. Tests

- [x] 5.1 Unit test `CandleCoverageService`: up-to-date timeframe, stale timeframe, and empty timeframe (null earliest/latest, assetCount 0, upToDate false) using mocked repo + fixed clock
- [x] 5.2 Integration/controller test hitting `GET /api/v1/candles/coverage` returns entries for D1 and W1
- [x] 5.3 Run the affected test suite and confirm green
