## 1. Normalize MEXC close times

- [x] 1.1 In `MexcMarketDataProvider.toCandle`, set `closeTime = openTime + 1 day - 1ms` (mirroring `TiingoMarketDataProvider.toCandle`) and document why `row[6]` isn't used
- [x] 1.2 Update `MexcMarketDataProviderTest`: a kline with `close = next open` parses to `open + 1d - 1ms`; `Timeframe.D1.openTimeFor(closeTime)` round-trips to the open time

## 2. Resume from open time

- [x] 2.1 Add `CandleDao.findLatestOpenTime(long assetId, Timeframe)` (mirrors `findLatestCloseTime`) with a DAO integration test
- [x] 2.2 `CandleIngestionService.ingestForAsset`: start from `findLatestOpenTime(D1) + 1ms` instead of `findLatestCloseTime + 1`
- [x] 2.3 `fetchAndUpsertUntilCaughtUp`: `nextWindowStart = last.openTime + 1ms`; keep the non-advancing-window guard
- [x] 2.4 Extend `CandleIngestionServiceTest`'s fake provider to page (N bars per call, honouring `open >= startTime`) with a configurable close convention; add tests: MEXC-style paging across a boundary has no missing day; incremental run after a stored bar includes the next day; Binance-style paging has no gap and no duplicate fetch

## 3. Gap warnings

- [x] 3.1 In `fetchAndUpsertUntilCaughtUp`, track the previous bar's open (seeded from the latest stored open) and log WARN with symbol and missing range when consecutive opens differ by more than 1 day; keep storing all bars
- [x] 3.2 Test: a fake page with a missing day logs the warning (capture via the existing log-assertion approach in `zeroCandlesDuringAnExpectedFetchWindowIsLoggedNotSilent`) and stores the returned bars

## 4. Weekly rollup timing

- [x] 4.1 Add a `CandleRollupServiceTest` case: with normalized close times, a complete week is rolled up when the clock is Monday 00:30 UTC of the following week

## 5. Data repair migration

- [x] 5.1 Add `V27__reset_mexc_market_data.sql`: delete `signal_state`, then `indicator_supertrend`, then `candle` rows for `asset.provider = 'MEXC'`, with a header comment explaining why and that the next run re-backfills
- [x] 5.2 Verify the SQL against a clone of a real Columbo DB (e.g. Monty's local one): FK order, MEXC rows gone, non-MEXC rows unchanged. **Prerequisite:** free Docker disk space (was 100% full on 2026-10-09)
- [x] 5.3 Migration integration test: seed MEXC and BINANCE assets with candles/indicators/signals at V26, migrate to V27, assert MEXC rows gone and BINANCE rows intact

## 6. Backfill horizon and docs

- [x] 6.1 Set `SUPERTREND_BACKFILL_START=2024-07-01T00:00:00Z` in `compose.yaml` and the README examples; note the reason (full-history SuperTrend flips) next to it
- [x] 6.2 Add a README/developer-notes entry: post-deploy steps (trigger `POST /api/v1/internal/ingestion/run`, verification queries), and the provider close-time convention invariant
- [x] 6.3 Production uses `compose.yaml` (`compose.prod.yaml` only overrides the image), so 6.1 covers it - unless the deploy host overrides it via `.env` (operator to check)

## 8. MEXC empty pre-listing windows (found in 7.4)

- [x] 8.1 `MexcMarketDataProvider.fetchDailyCandles`: skip forward by 500 days while a window is empty and before `endTime`
- [x] 8.2 `MexcMarketDataProviderTest`: stub server mimicking MEXC's 500-day window returns the listing-day bars for a start 600 days earlier (2 requests), and an all-empty range terminates
- [x] 8.3 Re-run the end-to-end backfill: all 96 active assets have D1 data up to yesterday with no gaps

## 7. End-to-end verification

- [x] 7.1 Full test suite green
- [x] 7.2 Run the fixed app against a fresh DB with live MEXC and backfill start 2024-07-01; the missing-days query returns no rows:
      `with days as (select generate_series('2024-07-01'::date, current_date - 1, '1 day')::date d) select d from days where d not in (select distinct open_time::date from candle where timeframe = 'D1');`
- [x] 7.3 BTCUSDT W1 has the 2025-11-10 bar, `BEARISH_REVERSAL` on 2025-11-10 and `BULLISH_REVERSAL` on 2026-08-31 (compare with TradingView SuperTrend(10, hl2, 3) on BTCUSD weekly)
- [x] 7.4 Apply V27 to a clone of an existing gapped DB, run ingestion, and re-run 7.2/7.3 on it
