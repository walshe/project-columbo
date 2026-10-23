## Why

Columbo stores every active asset's D1/W1 OHLCV candles, SuperTrend series and trend-flip events, but no endpoint returns them as a time series - only `/api/v1/candles/coverage` (history extent) and point-in-time signal summaries. Monty (the frontend) wants to render interactive candlestick charts of Columbo's own data, starting with a TradingView Lightweight Charts trial branch and possibly a KLineChart branch after, so Columbo needs one read API both can consume. Keeping it library-agnostic also makes it a usable tool for the AI agent that calls this API.

## What Changes

- New `GET /api/v1/candles` endpoint: returns one active asset's finalized candles for a timeframe as an ascending array of `{time, open, high, low, close, volume}`, where `time` is the candle's **open time in UTC epoch seconds** and prices/volume are JSON numbers. Windowed by optional `from`/`to` and a `limit` (most recent N within the window), with a `hasMore` flag so a chart can lazily page older history.
- Optional overlay via `include=supertrend`: the stored SuperTrend line (`{time, value, direction}`) and trend-flip markers (`{time, event}` for `BULLISH_REVERSAL`/`BEARISH_REVERSAL`), time-aligned to the same candle window.
- Response metadata: `symbol`, `name`, `timeframe`, `assetClass`, `tradingviewUrl` (same builder as everywhere else) and the standard freshness metadata.
- Rich `@OpenApi` documentation suitable for an agent tool surface.
- No library-specific naming in paths or fields; existing `tradingviewUrl` links in other responses are unchanged.

## Capabilities

### New Capabilities
- `candle-chart-data`: Time-series read access to an asset's stored candles, with an optional SuperTrend line and flip-marker overlay, shaped for any client-side charting library.

### Modified Capabilities
<!-- None - no existing spec requirements change. -->

## Impact

- **API**: new `GET /api/v1/candles` route (additive; `/api/v1/candles/coverage` keeps working - Javalin matches the literal `/coverage` path).
- **Code**: new `CandlesHandler` + response records in `api/`; new windowed read methods on `CandleDao`, `SuperTrendIndicatorDao`, `SignalStateDao`; an active-asset-by-symbol lookup on `AssetDao`; wiring in `Main`.
- **Database**: no schema changes or migrations; reads use the existing `(asset_id, timeframe, close_time)` unique indexes.
- **Consumers**: Monty adds a small adapter per chart library (e.g. multiply `time` by 1000 for KLineChart). Monty reaches Columbo server-side (Supabase functions), so no CORS change is needed here.
