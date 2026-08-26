## Why

Weekly briefing endpoints appeared to only ever surface Binance-tokenized assets — never the 47 real Tiingo equities — even after `add-tradingview-exchange-ref` fixed link generation itself. Root cause: `ScanSort.LIQUIDITY_DESC` (used by both weekly briefings' scan-based candidate sections, capped to the top 15) ranks assets by `v_asset_liquidity`'s raw `AVG(candle.volume)`. `candle.volume` means different things per provider — Binance's is quote-asset (USDT) **dollar volume**; `TiingoMarketDataProvider` was storing Tiingo's raw **share count**. Comparing the two in one ranking is comparing incompatible units, and likely crowded Tiingo assets out of every liquidity-gated section entirely.

## What Changes

- `TiingoMarketDataProvider.toCandle` now stores `candle.volume` as dollar-notional value traded (`adjVolume * adjClose`), matching Binance's existing "quote-asset volume" semantics, so `AVG(candle.volume)`-based liquidity ranking is meaningful across providers.
- One-time backfill migration correcting every already-ingested `TIINGO`-provider candle's stored `volume` to the same dollar-notional value, using its own already-adjusted stored `close`.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
(none tracked as formal specs yet for the `tiingo-market-data` capability's candle-mapping behavior from `add-tiingo-provider` — captured here as a delta requirement against that behavior instead, see specs/)

## Impact

- `TiingoMarketDataProvider` (candle volume field mapping).
- New migration backfilling historical Tiingo candle data.
- Downstream: `v_asset_liquidity`, `AssetLiquidityDao`, `ScanSort.LIQUIDITY_DESC` (`ScanService`/`SignalQueryService`), and both weekly briefing endpoints' scan-based candidate sections — no code changes needed there, they become correct once the underlying stored value is.
- No change to SuperTrend indicator computation, candle OHLC values, or any pipeline phase besides what's stored in `candle.volume` for Tiingo assets specifically.
