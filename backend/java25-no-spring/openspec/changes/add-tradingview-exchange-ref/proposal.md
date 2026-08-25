## Why

`add-tiingo-provider` made `TradingViewUrl.generateUrl` return `null` for every `EXCHANGE`-venue (Tiingo) asset, since Binance-shaped link construction (USDT pairing, provider name as exchange prefix) would produce a wrong link (e.g. `TIINGO:AAPLUSDT`) for a real equity. That was the safe stopgap, but it means none of the 47 real Tiingo assets get a working chart link at all. TradingView itself does carry every one of these listings, under their real exchange (`NASDAQ`, `NYSE`, `OTC` for ADRs, `SSE` for Shanghai A-shares) — this data just wasn't being captured anywhere.

## What Changes

- Add a nullable `tradingview_ref` column on `asset` storing a verified `EXCHANGE:SYMBOL` TradingView reference (e.g. `NASDAQ:AAPL`, `OTC:SSNLF`, `SSE:601398`, `NYSE:BRK.A`).
- Seed it for all 47 `V17` Tiingo assets, each individually verified against TradingView's own symbol search (not guessed from Tiingo's own `exchangeCode` metadata, which uses different labels and doesn't always match TradingView's ticker format — e.g. Tiingo's `BRK-A` vs. TradingView's `BRK.A`).
- `TradingViewUrl.generateUrl` gains a `tradingviewRef` parameter: for `EXCHANGE`-venue assets it builds the chart URL directly from this verified ref when present, and still returns `null` when absent (no fabricated link) — `SPOT`/`FUTURES` behavior is unchanged.

## Capabilities

### New Capabilities
- `tradingview-chart-links`: verified, per-asset TradingView chart deep links for real-equity (Tiingo/`EXCHANGE`-venue) assets, sourced from a stored `tradingview_ref` rather than derived from provider/symbol/venue alone.

### Modified Capabilities
(none — `market-data-ingestion` and `tiingo-market-data` behavior are unaffected; this only changes what `SignalQueryService` passes into `TradingViewUrl.generateUrl` and what that function does with it)

## Impact

- Schema: new migration `V18` (add column), `V19` (seed the 47 verified refs).
- `Asset` record gains a `tradingviewRef` field; `AssetDao`'s SELECT/row-mapper updated.
- `TradingViewUrl.generateUrl` signature changes (adds `tradingviewRef` param) — its one call site (`SignalQueryService`) and all existing tests updated.
- No change to candle ingestion, signal detection, or any other pipeline stage — purely a read-path enrichment for the `tradingviewUrl` field already exposed on `/signals`/`/scan`/briefing responses.
