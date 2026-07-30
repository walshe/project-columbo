## Why

The existing `/api/v1/summary` endpoint is single-timeframe only — it cannot surface assets that are aligned on *both* the weekly and daily SuperTrend simultaneously. Cross-timeframe alignment (W1 bullish + D1 bullish, or W1 bearish + D1 bearish) is a stronger confluence signal and is the primary filter a trader would use before sizing into a position.

## What Changes

- New endpoint `GET /api/v1/summary/confluence` returns two lists: assets bullish on both W1 and D1 SuperTrend, and assets bearish on both W1 and D1 SuperTrend
- Each list is ordered by the date the D1 signal fired (the point when confluence became tradeable)
- Response includes both JSON and MARKDOWN format support (via `format` query param)
- No changes to the existing `/api/v1/summary` endpoint

## Capabilities

### New Capabilities

- `supertrend-confluence`: Cross-timeframe SuperTrend alignment endpoint that returns bull-aligned (W1+D1 bullish) and bear-aligned (W1+D1 bearish) asset lists, ordered by when the D1 signal fired

### Modified Capabilities

## Impact

- New controller: `ConfluenceSummaryController` under `api/v1/summary/`
- New service: `ConfluenceSummaryService` — queries signal states for both timeframes and joins on asset
- New DTO: `ConfluenceSummaryReport` record
- Reuses existing `SignalQueryService`, `ScanService`, `SummaryReportFormatter`, and `IngestionStatusService`
- No schema changes, no new DB queries beyond what `SignalQueryService` already supports
