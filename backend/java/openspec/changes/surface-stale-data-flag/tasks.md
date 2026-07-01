## 1. Shared freshness service

- [x] 1.1 Add `CandleFreshnessService` exposing `expectedLatest(Timeframe)` and `isUpToDate(Timeframe)` (per-timeframe finalized boundary via `TimeProvider` + latest stored close), moving the definition currently inline in `CandleCoverageService`
- [x] 1.2 Refactor `CandleCoverageService` to use `CandleFreshnessService` for `expectedLatest`/`upToDate`, leaving its output unchanged
- [x] 1.3 Add a grace-period-aware `isStaleForRequireFresh(Timeframe)` (or equivalent) that ignores the normal post-boundary ingestion window; grace as a named constant with a comment

## 2. Response flag

- [x] 2.1 Add a `stale` field to `SignalListResponse`, `SummaryReport`, and `ConfluenceSummaryReport`
- [x] 2.2 Populate `stale = !isUpToDate(timeframe)` where each response is assembled (D1 for the trend-alignment report)

## 3. requireFresh enforcement

- [x] 3.1 Add an optional `requireFresh` (default false) query param to `/signals`, `/summary`, and `/summary/trend-alignment`
- [x] 3.2 When `requireFresh` is true and the timeframe is stale beyond the grace period, return `503` with an explanatory body (and `Retry-After` if straightforward); otherwise serve normally

## 4. Tests

- [x] 4.1 Unit test `CandleFreshnessService`: up-to-date, stale, and grace-window cases with a fixed clock
- [x] 4.2 Test that responses carry `stale` correctly (fresh → false, stale → true) and that the flag matches the coverage endpoint's `upToDate`
- [x] 4.3 Test `requireFresh`: stale-beyond-grace → 503, stale-within-grace → normal, omitted → normal payload with `stale` true
- [x] 4.4 Run the affected test suite and confirm green
