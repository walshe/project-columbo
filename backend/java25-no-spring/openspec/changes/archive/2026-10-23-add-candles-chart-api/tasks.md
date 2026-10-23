## 1. Persistence reads

- [x] 1.1 Add `AssetDao.findActiveBySymbol(String symbol)` returning all active assets with that exact symbol
- [x] 1.2 Add `CandleDao.findLatestInOpenTimeWindow(Connection, assetId, timeframe, fromOpenInclusive, toOpenExclusive, limit)` returning up to `limit + 1` most-recent candles in the window (nullable bounds), ascending; cover with a DAO integration test (bounds, ordering, limit+1)
- [x] 1.3 Add `SuperTrendIndicatorDao.findBetween(Connection, assetId, timeframe, closeFrom, closeTo)` (both inclusive) ascending, with an integration test (`ChartOverlayDaoIntegrationTest`)
- [x] 1.4 Add `SignalStateDao.findFlipsBetween(Connection, assetId, timeframe, closeFrom, closeTo)` returning only non-`NONE` events ascending, with an integration test

## 2. API response model

- [x] 2.1 Add records `CandlesResponse` (symbol, name, assetClass, timeframe, tradingviewUrl, hasMore, freshness metadata, candles, nullable supertrend/signals omitted when null), `ChartCandle`, `SuperTrendPoint`, `SignalMarker`
- [x] 2.2 Map `time` from candle open time to epoch seconds; overlay rows take the `time` of the returned candle with the same close time (guaranteed alignment); confirm `BigDecimal` fields serialize as JSON numbers and null overlay fields are omitted

## 3. Handler

- [x] 3.1 Create `CandlesHandler` registering `GET /api/v1/candles`: parse/validate `symbol`, `timeframe`, `from`/`to` (instant or date), `limit` (default 500, 1..5000), `include` (only `supertrend`); 400 on invalid input
- [x] 3.2 Resolve symbol via `findActiveBySymbol`: 404 when empty, 409 listing providers when >1
- [x] 3.3 Load the candle window, compute `hasMore` from the extra row, and when `include=supertrend` load overlay rows bounded by the first/last returned close times on the same connection
- [x] 3.4 Populate `tradingviewUrl` via `TradingViewUrl.generateUrl` and freshness metadata via `FreshnessService` (no 503 path)
- [x] 3.5 Write rich `@OpenApi` docs (epoch-seconds open time, finalized-only, paging with `to` + `hasMore`, overlay contents, error codes)
- [x] 3.6 Wire `CandlesHandler` in `Main` and verify `/api/v1/candles/coverage` still routes correctly

## 4. Tests and verification

- [x] 4.1 `CandlesHandlerIntegrationTest` covering every spec scenario: ordering/epoch time, default limit + `hasMore`, paging without overlap, explicit range, invalid params, 404 inactive/unknown, 409 ambiguous, overlay alignment, NONE-filtering, overlay omitted by default, stale still 200
- [x] 4.2 Extend `OpenApiDocumentationIntegrationTest` to assert the new path and parameters
- [x] 4.3 Run the full test suite green; smoke-test against a local DB with `curl` (D1 and W1, with and without `include=supertrend`) and render one response in Lightweight Charts to confirm the shape works unchanged
