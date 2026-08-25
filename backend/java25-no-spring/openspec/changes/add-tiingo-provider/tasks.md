## 1. Schema

- [x] 1.1 Add `V15__add_tiingo_provider.sql`: `ALTER TYPE provider ADD VALUE 'TIINGO'`. Must be its own migration file (Postgres forbids using a new enum value in the same transaction that adds it).
- [x] 1.2 Add `V16__add_exchange_venue.sql`: `ALTER TYPE asset_venue ADD VALUE 'EXCHANGE'`. Same one-migration-per-enum-addition constraint as 1.1.
- [x] 1.3 Add `V17__seed_tiingo_assets.sql`: insert the 47 assets (see design.md's list) with `provider = 'TIINGO'`, `asset_class = 'STOCK'`, `venue = 'EXCHANGE'`, `active = true`, and `name` set explicitly per row (do not rely on column defaults for `venue`, unlike `V12__seed_stock_assets.sql`'s original mistake). Tickers: `NVDA, AAPL, GOOG, MSFT, AMZN, TSM, AVGO, TSLA, META, SSNLF, LLY, MU, BRK-A, JPM, WMT, AMD, V, XOM, ASML, JNJ, TCEHY, MA, INTC, ABBV, CSCO, PLTR, BAC, ORCL, COST, CVX, 601398, LRCX, KO, AMAT, CAT, MRK, RHHBY, GE, HSBC, UNH, 601288, MS, PG, HD, NFLX, 601939, GS`.

## 2. Domain model

- [x] 2.1 Add `TIINGO` to the `Provider` enum in `walshe.projectcolumbo.supertrend.shared`.
- [x] 2.2 Add `EXCHANGE` to the `AssetVenue` enum; update its Javadoc from "which Binance product an asset trades on" to a provider-general description (Binance spot/futures vs. `EXCHANGE` for a real securities exchange via Tiingo).

## 3. Tiingo market data provider

- [x] 3.1 Create `TiingoMarketDataProvider implements MarketDataProvider` in `walshe.projectcolumbo.supertrend.ingestion`, structurally parallel to `BinanceMarketDataProvider`: constructor overloads `(HttpClient, String apiKey)` (default base URL `https://api.tiingo.com`) and `(HttpClient, String apiKey, String baseUrl)` (test override hook), with `Objects.requireNonNull` guards including a non-blank check on `apiKey` so a missing key fails fast at construction, not on first request.
- [x] 3.2 Implement `fetchDailyCandles(symbol, startTimeMs, endTimeMs)`: call `GET /tiingo/daily/{symbol}/prices?startDate={yyyy-MM-dd}&endDate={yyyy-MM-dd}&token={apiKey}` (convert the millisecond window to Tiingo's calendar-date params), parse the JSON array of daily objects, and map each to a `Candle` using `adjOpen`/`adjHigh`/`adjLow`/`adjClose`/`adjVolume` (not the raw fields) so splits/dividends don't appear as price discontinuities.
- [x] 3.3 Handle Tiingo's not-found response (`{"detail": "Not found."}` / 404) by throwing `InvalidSymbolException`, mirroring `BinanceMarketDataProvider.handleErrorResponse`'s invalid-symbol handling, so `CandleIngestionService`'s existing auto-deactivation logic works unchanged for a Tiingo symbol that stops resolving.
- [x] 3.4 Do not add automatic retry logic in the client — a failed fetch should surface once per asset per run via the existing `ingestForAssetSafely` catch-and-continue (see design.md's rate-limit reasoning: this keeps worst-case Tiingo request volume at 47/run regardless of failures).
- [x] 3.5 Unit test `TiingoMarketDataProviderTest`: default/overridden base URL construction, date-window-to-calendar-date conversion, adjusted-field mapping to `Candle`, not-found response mapping to `InvalidSymbolException`.

## 4. Ingestion routing

- [x] 4.1 No interface or routing-map shape change needed in `CandleIngestionService` — confirm `providersByVenue.get(asset.venue())` continues to work unmodified once `EXCHANGE` is a valid map key (see design.md decision 1).
- [x] 4.2 Update `CandleIngestionServiceTest`: add a case proving an `EXCHANGE`-venue asset routes to its configured fake provider in the same run as `SPOT`/`FUTURES` assets, alongside the existing venue-routing cases.

## 5. Composition root & config

- [x] 5.1 `Main.java`: read `TIINGO_API_KEY` via `System.getenv` (same pattern as existing Binance base-URL overrides); construct a `TiingoMarketDataProvider` (with an optional `SUPERTREND_TIINGO_BASE_URL` override for tests, mirroring the existing Binance override pattern); add it to the `providersByVenue` map under `AssetVenue.EXCHANGE`.
- [x] 5.2 Confirm `DailyScheduler`'s existing `Provider.BINANCE` run-tracking label is left unchanged (see design.md — purely cosmetic, out of scope for this change).

## 6. Persistence test updates

- [x] 6.1 Update `AssetDaoIntegrationTest`'s seed helper/assertions if it enumerates `Provider`/`AssetVenue` values exhaustively anywhere, to account for `TIINGO`/`EXCHANGE`. Confirmed: no exhaustive enumeration exists (no `Provider.values()`/`AssetVenue.values()` usage in the test or in main code's `switch`es over these enums other than `BinanceMarketDataProvider`'s own SPOT/FUTURES-only switches, which are never called with `EXCHANGE`) — no change needed. seed helper/assertions if it enumerates `Provider`/`AssetVenue` values exhaustively anywhere, to account for `TIINGO`/`EXCHANGE`.

## 7. E2E test

- [x] 7.1 `PipelineEndToEndIT`: add a WireMock stub for Tiingo's `/tiingo/daily/{symbol}/prices` path, set `SUPERTREND_TIINGO_BASE_URL` (and a test `TIINGO_API_KEY`) to point at the WireMock instance.
- [x] 7.2 Add an assertion proving a real `EXCHANGE`-venue Tiingo asset (one of the 47 seeded symbols) actually ingests and produces signals within the same run as the existing Binance-sourced assets — not just that `assetCount` increased by 47.

## 8. Docs

- [x] 8.1 `README.md`: add `TIINGO_API_KEY` (and `SUPERTREND_TIINGO_BASE_URL` if introduced) to the env var table.
- [x] 8.2 `developer-notes.md`: document the `EXCHANGE` venue and the additive (non-deduplicated) coexistence of tokenized Binance and real Tiingo assets for the same company, so a future contributor doesn't assume it's a bug.

## 9. Verification

- [x] 9.1 Run full `mvn test` suite, confirm no regressions. (Found and fixed a real regression not called out in tasks.md: `PersistenceIntegrationTest.assetDaoFindsAllSeededActiveAssets` hardcoded an expected active-asset count of 200; updated to 247 to account for the 47 new `V17`-seeded Tiingo assets. All 248 tests pass.)
- [x] 9.2 Run `mvn verify -Pe2e`, confirm the new Tiingo-routing assertion passes. (Passed: `PipelineEndToEndIT` — 246 assets, real `AAPL` (Tiingo/EXCHANGE) shows `BULLISH` alongside tokenized `AAPLUSDT` (Binance/FUTURES).)
- [ ] 9.3 After deploy, confirm a live ingestion run actually pulls real data for a handful of the 47 assets (spot-check against Tiingo directly, same pattern as the live-verification already done for AAPL/SPY/QQQ/SMCI/RKLB before this change was scoped).
- [x] 9.4 Self-review via the `java-code-review` skill before opening the PR, plus the `pr_agent` CI check's review on PR #67. `pr_agent` raised 3 points: (1) `pricesUri`'s `URLEncoder.encode` uses form-encoding rules, wrong for a path segment — fixed by adding `.replace("+", "%20")`, matching `TradingViewUrl.encode`'s existing fix for the same mismatch; (2) `toCandle`'s `closeTime` assumed Tiingo's `date` field is always exact UTC midnight — fixed to derive it from the calendar date instead, since 3 of the 47 seeded assets are Shanghai-listed and 3 are OTC ADRs, not standard US-exchange listings; (3) `requireEnv("TIINGO_API_KEY")` being unconditional was flagged as a regression for Binance-only deployments — left as-is, since this matches the `tiingo-market-data` spec's explicit "Missing API key" requirement (fail fast at startup) and this system has no such thing as a Tiingo-optional deployment variant. No Critical/High findings. Two real gaps found and fixed during implementation, ahead of this pass: (1) `BinanceMarketDataProvider`'s venue switches became non-exhaustive once `EXCHANGE` was added — fixed by throwing `IllegalArgumentException` for that case, since a Binance client should never be constructed with it; (2) `TradingViewUrl.generateUrl` would have fabricated a nonsensical link (e.g. `TIINGO:AAPLUSDT`) for `EXCHANGE`-venue assets — fixed to return `null` instead, since no real per-asset TradingView exchange is stored. Also added `TIINGO_API_KEY` to `compose.yaml`'s prod app service (sourced from the deploying environment, never committed), which `tasks.md` didn't call out but was needed since `Main.requireEnv` now fails fast without it. Minor/Low notes, not acted on: `TiingoMarketDataProvider.pricesUri` uses `URLEncoder.encode` (form-encoding rules) rather than a path-segment encoder for the symbol — harmless given real Tiingo tickers are always alphanumeric/dash; Tiingo's API itself requires the API key as a URL query param (`&token=`), not a header — inherent to Tiingo's contract, not fixable client-side, and the key is never logged.
