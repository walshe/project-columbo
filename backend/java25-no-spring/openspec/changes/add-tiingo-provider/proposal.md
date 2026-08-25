## Why

Every STOCK/ETF asset today is sourced from Binance's tokenized `TRADIFI_PERPETUAL` contracts — synthetic trackers that trade near-continuously like crypto, not the real underlying equity on its actual exchange. Tiingo has been live-verified (real auth, correct response shape, decades of genuine daily EOD history, and spot-checked coverage across mega-cap, mid-cap, and small/recent-IPO names) as a real-data alternative. We're adding it as a second provider so a first batch of real, market-cap-ranked equities can be tracked against actual exchange data, additively alongside the existing tokenized Binance assets (no removal or migration of those yet — that's a separate future decision).

## What Changes

- Add `TIINGO` to the `provider` type — both the Postgres `provider` ENUM (new migration, `ALTER TYPE provider ADD VALUE 'TIINGO'`) and the Java `Provider` enum.
- Add a Tiingo market data client (`GET /tiingo/daily/<ticker>/prices`) analogous to the existing Binance client, authenticated via a `TIINGO_API_KEY` loaded from config/environment. Tiingo is daily-EOD-only — no intraday bars — so it only ever feeds the `D1` timeframe (`W1` is still derived from `D1` the same way it already is for Binance assets).
- Route ingestion per-asset by `provider`, the same way it's already routed per-asset by `venue` (`AssetVenue`) since add-asset-venue-routing — Tiingo assets go to the Tiingo client, Binance assets keep going to the venue-scoped Binance clients.
- Pace Tiingo ingestion to stay within its free-tier caps (1000 requests/day, 50/hour) — comfortably enough headroom for the 47-asset seed batch, but the scheduling needs to account for retries not blowing through the hourly cap.
- Decide and document the correct `AssetVenue` value for real Tiingo equities (the enum was designed for Binance's spot-vs-futures split and doesn't obviously map to a real exchange-traded equity) — either reusing `SPOT` with documented reasoning, or adding a new value.
- Seed migration onboarding 47 real, live-verified equities as new `TIINGO`-provider, `STOCK`-class rows: NVDA, AAPL, GOOG, MSFT, AMZN, TSM, AVGO, TSLA, META, SSNLF (Samsung ADR), LLY, MU, BRK-A, JPM, WMT, AMD, V, XOM, ASML, JNJ, TCEHY (Tencent ADR), MA, INTC, ABBV, CSCO, PLTR, BAC, ORCL, COST, CVX, 601398 (ICBC), LRCX, KO, AMAT, CAT, MRK, RHHBY (Roche ADR), GE, HSBC, UNH, 601288 (Agricultural Bank of China), MS, PG, HD, NFLX, 601939 (China Construction Bank), GS — each populated with its real company name (already-existing, previously-unused `name` column, now wired into `Asset`/`AssetDao` in this same body of work) sourced from Tiingo's metadata.
- Existing tokenized Binance STOCK/ETF assets are untouched — no deactivation, no dedup logic. Both sets of assets for the same underlying company (e.g. a tokenized Binance `AAPL` tracker and the real Tiingo `AAPL`) are expected to coexist and appear as separate rows.

## Capabilities

### New Capabilities
- `tiingo-market-data`: Tiingo is a real-equity market data provider — an authenticated daily-EOD client, rate-limit-aware ingestion, and the initial 47-asset onboarding seed (including real company display names).

### Modified Capabilities
- `market-data-ingestion`: ingestion becomes provider-aware, not just venue-aware — a second provider (Tiingo) with different mechanics from Binance (API-key auth, daily-only bars, its own rate limits) must be scheduled and routed to correctly per asset.

## Impact

- **Schema**: new migration adding `TIINGO` to the `provider` enum; new seed migration inserting the 47 assets (with `name` populated).
- **Code**: `Provider` enum, a new `TiingoMarketDataProvider` (or equivalent) client, `CandleIngestionService` (provider-based routing), ingestion scheduling/config for the new rate limits, `Main` (composition root) to wire the new client with its API key.
- **Config**: new `TIINGO_API_KEY` (and likely a base-URL override for testing, mirroring the Binance spot/futures pattern) needs a config-loading mechanism — `java25-no-spring` has no existing `.env`/config-file wiring, so this change needs to establish one (or confirm plain environment variables are sufficient, consistent with how DB connection config is already loaded).
- **Out of scope**: no changes to existing Binance-sourced STOCK/ETF assets; no dedup/merge logic between tokenized and real versions of the same company; Saudi Aramco, SK hynix, and SpaceX's `SPCX` are deliberately excluded from the seed (no usable Tiingo-covered ticker for the first two; `SPCX` resolves but is almost certainly a private-SpaceX-exposure product, not real equity).
