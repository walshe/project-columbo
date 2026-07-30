## Why

When SuperTrend flips didn't line up with TradingView, the actual cause was incomplete candle data (a truncated backfill), not the indicator math — but there was no quick way to see that. Today the only freshness signals are the `lastIngestionAt` / `candlesThrough` fields buried in other responses. A dedicated coverage endpoint lets an operator answer "is our candle history deep enough and current?" at a glance, which is exactly the check that would have short-circuited that debugging session.

## What Changes

- Add `GET /api/v1/candles/coverage` returning a per-timeframe summary for each `Timeframe` (D1, W1):
  - `earliest` — close time of the oldest stored candle
  - `latest` — close time of the newest stored candle
  - `expectedLatest` — the most recent *finalized* close time for that timeframe as of now
  - `upToDate` — whether `latest` has reached `expectedLatest`
  - `assetCount` — number of distinct assets with at least one candle in that timeframe
- Add the repository aggregate queries needed to populate it (earliest/latest close time and asset count per timeframe)
- Read-only, no auth changes; complements the freshness fields already returned by other endpoints

## Capabilities

### New Capabilities

- `candle-coverage`: A read endpoint reporting, per timeframe, the stored candle range, the expected latest finalized close, an up-to-date flag, and the covered asset count.

### Modified Capabilities

_(none)_

## Impact

- New `CandleCoverageController` (`GET /api/v1/candles/coverage`) + `CandleCoverageService` + response DTO
- `CandleRepository` — new per-timeframe aggregate queries (earliest/latest close time, distinct asset count)
- Reuses the finalized-boundary logic already used by the ingestion pipeline to compute `expectedLatest`
- **Out of scope**: per-asset breakdown (this change is the per-timeframe summary only), alerting/notifications, and any write/backfill actions
