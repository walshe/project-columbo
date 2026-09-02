## Why

Tiingo's free-tier rate limit (50/hour) already puts a fresh-restart-and-ingest test cycle at ~94% of the hourly budget, and Binance's tokenized STOCK/ETF contracts were already retired once (`fix-pipeline-connection-pool-exhaustion`) for being a fan-out liability. MEXC's public `/api/v3/klines` endpoint is unauthenticated, rate-limited at 500 req/10s (weight 1 per call) — roughly 60x more headroom than Tiingo at the same asset count — and its `/api/v3/exchangeInfo` response carries both a human-readable `fullName` and a `conceptPlates` tag (`mc-trade-zone-xStocks`) that reliably identifies its ~100+ Ondo-tokenized real-equity pairs alongside its ordinary crypto pairs. One provider can therefore plausibly cover CRYPTO, STOCK, and ETF asset classes through a single client and a single kline shape (confirmed byte-identical to Binance's spot klines array), removing two providers' worth of client code, rate-limit math, and API-key management in exchange for one.

## What Changes

- Add `MexcMarketDataProvider implements MarketDataProvider` calling `GET /api/v3/klines` (no API key required) — structurally near-identical to `BinanceMarketDataProvider` given the identical response shape.
- Add `Provider.MEXC` and `AssetVenue.MEXC` (new Postgres enum values via Flyway), and wire `AssetVenue.MEXC -> MexcMarketDataProvider` into `CandleIngestionService`'s existing `providersByVenue` map — no changes to `CandleIngestionService`, `PipelineOrchestrator`, or `DailyScheduler` needed, matching how Tiingo/`EXCHANGE` was added with zero orchestration changes.
- Add asset-classification logic (used at onboarding time, not at ingestion time) that calls `GET /api/v3/exchangeInfo` and classifies each USDT-quoted, tradeable symbol as `STOCK`/`ETF` (via the `conceptPlates` `mc-trade-zone-xStocks` tag, then `fullName` containing "ETF") or `CRYPTO` (everything else) — **not** the `baseAsset`-suffix heuristic, which produces false positives against live data (`BOSONUSDT`, a real crypto token, ends in "ON" but is not `xStocks`-tagged).
- Seed migration onboarding a new, user-curated MEXC-sourced asset universe: top-100-by-market-cap crypto, stock, and ETF lists, each live-matched against MEXC's `exchangeInfo`, then capped at the top 50 *tradeable* names by rank for crypto and stock (ETF's match count never approaches 50, so it's kept uncapped) — same per-class cap size as `V22`, chosen over bounding ingestion concurrency to keep `CandleIngestionService` untouched. Final count: 118 assets (50 CRYPTO + 50 STOCK + 18 ETF). Full list, drops, and rank-51+ reserves are in [asset-list.md](asset-list.md).
- **BREAKING (data, not schema)**: deactivate (`active = false`, not deleted) every `BINANCE`- and `TIINGO`-provider asset row — "disconnect for now" per explicit request. Historical candle/indicator/signal rows for these assets are preserved untouched, matching this project's established deactivate-don't-delete convention (`V14`, `V22`).
- `Main.java`: stop hard-requiring `TIINGO_API_KEY` at startup (`requireEnv`) now that zero active assets route to `EXCHANGE` — construct the Tiingo client and its `providersByVenue` entry only when the key is actually configured, so the app can start with Tiingo fully dormant. Binance client construction is unconditional either way (no required secret) and left wired but will simply route zero active assets.
- No change to `TradingViewUrl` — its existing generic branch (`provider.name() + ":" + fullSymbol`) already produces `MEXC:<SYMBOL>` for any non-`EXCHANGE` venue with no code change, pending live spot-check that TradingView actually resolves those symbols under the `MEXC` exchange prefix.

## Capabilities

### New Capabilities
- `mexc-market-data`: MEXC as a market-data provider — kline retrieval, asset classification via `exchangeInfo`, and rate-limit posture.

### Modified Capabilities
- `market-data-ingestion`: adds MEXC as a third routed provider/venue alongside Binance and Tiingo, and records that Binance- and Tiingo-provider assets are deactivated (not removed) as part of this change.

## Impact

- New: `MexcMarketDataProvider.java` (+ test), `MexcAssetClassifier` (or equivalent) for onboarding-time classification, new Flyway migrations for the `MEXC` provider/venue enum values, the asset-deactivation migration, and the MEXC seed migration.
- Changed: `Main.java` (MEXC wiring, optional Tiingo wiring), `AssetVenue.java` Javadoc.
- Unchanged: `CandleIngestionService`, `PipelineOrchestrator`, `DailyScheduler`, `TradingViewUrl`, all API handlers — this is purely a provider/routing + data change, consistent with how `add-tiingo-provider` required no orchestration changes either.
- `BinanceMarketDataProvider` and `TiingoMarketDataProvider` classes, tests, and migrations are left in place, untouched and unused-by-active-data — reversible by reactivating rows, not by redeploying code.
