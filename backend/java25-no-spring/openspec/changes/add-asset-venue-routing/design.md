## Context

Discovered by running the real app against a fresh database with the full 200-asset seed (crypto + the newly-added stock/ETF batch): every asset is fetched via `BinanceMarketDataProvider`'s single configured base URL, which defaults to Binance spot. 140 stock/ETF assets and 4 crypto assets (`HYPEUSDT`/`CCUSDT`/`MUSDT`/`KASUSDT`) only exist on Binance futures, so all 144 got `-1121 Invalid symbol` from spot and were auto-deactivated by `CandleIngestionService`'s existing (correct, for genuinely-bad symbols) self-healing behavior. Verified directly against both real APIs and against the futures `exchangeInfo` reference dump (`reference/binance_klines.json`) before writing this: spot and futures are separate Binance products with separate hosts *and* separate klines paths (`/api/v3/klines` vs `/fapi/v1/klines`), and a symbol's presence in the futures `exchangeInfo` dump says nothing about spot availability (`BTCUSDT`, which works fine today, and `HYPEUSDT`, which doesn't, both show up there identically).

## Goals / Non-Goals

**Goals:**
- Every asset gets fetched from the Binance venue it actually trades on.
- An already-running deployment self-heals (re-activates the wrongly-deactivated assets) when this migration runs, not just a fresh database.
- Keep `MarketDataProvider`'s interface asset-agnostic (`fetchDailyCandles(symbol, start, end)`) — venue routing is a concern of *which provider instance* handles a given asset, not a new parameter every implementation must accept.

**Non-Goals:**
- No general multi-provider/multi-exchange abstraction — this is Binance-spot vs Binance-futures specifically, not a pluggable venue system for arbitrary future exchanges.
- No fix for the 11 crypto symbols that don't exist on *either* venue (`WBTUSDT`, `LEOUSDT`, `RAINUSDT`, `CROUSDT`, `MNTUSDT`, `OKBUSDT`, `BGBUSDT`, `HTXUSDT`, `KCSUSDT`, `GTUSDT`, `FLRUSDT`) — those are pre-existing bad seed data (several look like rival exchanges' native tokens, never Binance-listed), unrelated to venue routing, and stay correctly deactivated.

## Decisions

- **A `venue` column on `asset`, not inferred from `asset_class` at request time.** `STOCK`/`ETF`/`COMMODITY` do map cleanly to `FUTURES` today, but 4 `CRYPTO` assets also need `FUTURES` — a clean `asset_class` → venue mapping doesn't hold, so venue has to be its own fact about the asset, stored explicitly. This mirrors the reasoning that already justified `asset_class` itself as a real column instead of inferring category from symbol patterns.
- **`MarketDataProvider` interface stays asset-agnostic; routing happens one layer up, in `CandleIngestionService`.** The alternative — adding a `venue` parameter to `fetchDailyCandles` — would leak a Binance-specific concept into an interface designed to abstract over "some market data source," and every test double/future implementation would carry a parameter it might not need. Instead, `CandleIngestionService` is constructed with a `Map<AssetVenue, MarketDataProvider>` and picks the right one per asset via `asset.venue()`, which it already has in scope from `AssetDao.findAllActive()`.
- **Two independently-overridable env vars, not one base URL plus a path-suffix convention.** `SUPERTREND_BINANCE_SPOT_BASE_URL`/`SUPERTREND_BINANCE_FUTURES_BASE_URL` replace the single `SUPERTREND_BINANCE_BASE_URL`. Each `BinanceMarketDataProvider` instance is now constructed *for* a venue and carries that venue's correct default host **and** klines path baked in (spot and futures don't just differ by host — `/api/v3/klines` vs `/fapi/v1/klines` — so a single overridable "base URL" was never going to be enough even before this change; it happened to work only because nothing needed the futures path yet).
- **The E2E test's existing single-endpoint WireMock setup is the reason this bug shipped silently — extend it to cover both paths, not just re-point it.** The WireMock catch-all stub matches on symbol, not real Binance venue rules, so it answered every symbol "successfully" regardless of which venue it would really belong to, and the E2E suite's `assetCount` assertion never caught the venue bug even though it was seeding assets that need `FUTURES` at the time. Adding matching stubs under `/fapi/v1/klines` (not just `/api/v3/klines`) and asserting on at least one futures-venue asset actually ingesting closes that gap so a future regression here would fail the E2E suite, not just get discovered by manually running the real app.

## Risks / Trade-offs

- [Re-activating previously-deactivated assets in the migration assumes today's known-bad list (4 crypto symbols) is complete] → Verified directly against the real Binance spot and futures APIs (not assumed) before writing this — see the conversation history / commit for the actual `curl` output. If another futures-only crypto asset is discovered later, it's the same one-line `UPDATE ... WHERE symbol IN (...)` pattern to add it, not a schema change.
- [`SUPERTREND_BINANCE_BASE_URL` → two variables is a breaking config rename] → Not set in `compose.yaml`/`compose.prod.yaml` today (both rely on the code default), so no production deployment config needs updating; only the E2E test's own container wiring references it, which this change updates in the same commit.

## Migration Plan

1. New migration: `CREATE TYPE asset_venue AS ENUM ('SPOT', 'FUTURES')`, add `venue asset_venue NOT NULL DEFAULT 'SPOT'` to `asset`, backfill `FUTURES` for `STOCK`/`ETF`/`COMMODITY` assets and the 4 named crypto symbols, and re-activate (`active = true`) any of those same assets that are currently inactive (heals an already-affected running deployment in the same step).
2. Ship schema + code changes together, per this project's standing convention (no phased rollout).
3. Rollback: forward-only, per this project's existing Flyway convention.

## Open Questions

- None.
