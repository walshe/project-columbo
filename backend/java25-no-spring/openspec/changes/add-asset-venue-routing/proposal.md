## Why

Every asset currently hits Binance's **spot** API (`api.binance.com/api/v3/klines`), regardless of class. That's wrong for two real, currently-broken groups: all 140 STOCK/ETF assets only exist on Binance's **futures** product (`fapi.binance.com/fapi/v1/klines` — different host *and* different path, not just a different domain), and 4 of the original crypto assets (`HYPEUSDT`, `CCUSDT`, `MUSDT`, `KASUSDT`) are genuine Binance pairs that happen to be futures-only, never spot-listed. All 144 of these get an `Invalid symbol` response from spot and are auto-deactivated by the existing self-healing logic — not because they're bad data, but because the app only ever asks the venue that doesn't have them.

## What Changes

- Add a `venue` column (`SPOT`/`FUTURES`) to `asset`, defaulting to `SPOT`. Backfill `FUTURES` for every `STOCK`/`ETF`/`COMMODITY` asset and for the 4 known futures-only crypto symbols.
- Re-activate any asset that was previously auto-deactivated for exactly this reason (the 4 crypto symbols plus every STOCK/ETF asset), so an already-running deployment self-heals when this migration runs, not just a fresh one.
- `BinanceMarketDataProvider` becomes venue-scoped: one instance per venue, each with its own correct default base URL *and* klines path (spot: `api.binance.com` + `/api/v3/klines`; futures: `fapi.binance.com` + `/fapi/v1/klines`), each independently overridable for testing.
- `CandleIngestionService` routes each asset to the right provider instance based on `asset.venue()` instead of using one single globally-configured provider.
- `SUPERTREND_BINANCE_BASE_URL` (one env var) is replaced by `SUPERTREND_BINANCE_SPOT_BASE_URL` / `SUPERTREND_BINANCE_FUTURES_BASE_URL` (two, independently overridable). **BREAKING** for any deployment that currently sets the old variable — it's Docker/E2E-test only today (`compose.yaml`/`compose.prod.yaml` don't set it in production), so low real-world impact.
- Extend the E2E test's WireMock stubbing to cover both klines paths and prove venue routing actually works — the existing test's single-endpoint stub setup accidentally responded successfully to every symbol regardless of real Binance venue rules, which is exactly why this bug shipped without the E2E suite catching it.

## Capabilities

### New Capabilities
- (none)

### Modified Capabilities
- `market-data-ingestion`: candle fetching is now venue-aware (spot vs futures) per asset, instead of always using one globally-configured Binance endpoint.

## Impact

- **Schema**: new migration adding `venue` to `asset`, backfilling known-futures-only assets, and re-activating assets wrongly deactivated by this bug in any database that already ran ingestion.
- **Code**: `AssetVenue` (new enum), `Asset`, `AssetDao`, `BinanceMarketDataProvider`, `CandleIngestionService`, `Main` (composition root), `PipelineEndToEndIT`.
- **Config**: `SUPERTREND_BINANCE_BASE_URL` → `SUPERTREND_BINANCE_SPOT_BASE_URL` / `SUPERTREND_BINANCE_FUTURES_BASE_URL`. `compose.prod.yaml`/`README.md`'s env var table need the rename reflected.
- **Immediate effect**: once deployed, all 140 stock/ETF assets and the 4 futures-only crypto assets should successfully ingest instead of being deactivated on first run.
