## 1. Provider/venue enum + client

- [x] 1.1 Add `Provider.MEXC` to `walshe.projectcolumbo.supertrend.shared.Provider`
- [x] 1.2 Add `AssetVenue.MEXC` to `walshe.projectcolumbo.supertrend.shared.AssetVenue`, update its Javadoc
- [x] 1.3 New migration: `ALTER TYPE provider ADD VALUE 'MEXC'` (`V23__add_mexc_provider.sql`)
- [x] 1.4 New migration: `ALTER TYPE asset_venue ADD VALUE 'MEXC'` (`V24__add_mexc_venue.sql`)
- [x] 1.5 Add `MexcMarketDataProvider implements MarketDataProvider` (`/api/v3/klines`, no API key, 8-field row parsing copied from `BinanceMarketDataProvider.toCandle`), plus unit tests mirroring `BinanceMarketDataProviderTest`
- [x] 1.6 Wire `AssetVenue.MEXC -> MexcMarketDataProvider` into `Main`'s `providersByVenue` map

## 2. Make Tiingo optional at startup

- [x] 2.1 Replace `Main`'s `requireEnv("TIINGO_API_KEY")` with an `envOrEmpty`-style optional check; only construct `TiingoMarketDataProvider` and add its `providersByVenue` entry when the key is present
- [x] 2.2 Test: application/pipeline startup succeeds with `TIINGO_API_KEY` unset and no active `EXCHANGE`-venue asset — folded into 6.2's e2e rewrite rather than a separate throwaway test (same heavyweight Docker-container startup either way); confirmed passing there

## 3. Asset classification + onboarding data

- [x] 3.1 One-off classification pass over live `GET /api/v3/exchangeInfo`: for each USDT-quoted, tradeable symbol, apply the `conceptPlates`-based classification rule (Decision 3) — done during design; see [asset-list.md](asset-list.md)
- [x] 3.2 Match the user's top-100-by-market-cap crypto list against MEXC's USDT pairs (93/100 direct matches), cap to top 50 tradeable by rank — done, see asset-list.md
- [x] 3.3 Match the user's top-100-by-market-cap stock list against MEXC's `xStocks`/Ondo wrappers (64/100 matched), cap to top 50 tradeable by rank; ETF list matched separately (18/100, uncapped) — done, see asset-list.md
- [x] 3.4 Checked `klines` history depth for all 118 final symbols (not just 2-3) against the ~147-bar W1 warm-up: all 50 crypto clear it; 5 stocks (`SPCXONUSDT`, `SKHYONUSDT`, `DELLONUSDT`, `WFCONUSDT`, `SAPONUSDT`) and 3 ETFs (`IWFONUSDT`, `EFAONUSDT`, `SOXXONUSDT`) are short — recorded in design.md Risks as an expected, self-resolving "new asset" transient, not a blocker
- [x] 3.5 Final human skim of `asset-list.md`'s 118 rows against the source lists before writing the seed migration — verified programmatically, exact match, no transcription errors
- [x] 3.6 New seed migration inserting the 118 assets from `asset-list.md` (`V25__seed_mexc_assets.sql`)

## 4. Deactivate Binance/Tiingo

- [x] 4.1 New migration deactivating every `BINANCE`/`TIINGO` asset row (`V26__deactivate_binance_and_tiingo.sql`) — sequenced after the seed migration so no asset class ever has zero active assets mid-migration

## 5. TradingView link verification

- [x] 5.1 Automated check was inconclusive (TradingView's chart page is a client-rendered SPA); user manually confirmed both `MEXC:BTCUSDT` and `MEXC:AAPLONUSDT` chart links resolve correctly on tradingview.com
- [x] 5.2 Not needed — both links resolve, no `TradingViewUrl` fallback/override required

## 6. Regression checks

- [x] 6.1 Ran `CandleIngestionServiceTest`, `PersistenceIntegrationTest`, `TradingViewUrlTest` (full non-e2e suite, 269 tests) — green; fixed 2 asset-count assumptions in `PersistenceIntegrationTest` that hardcoded the old 97-active/47-Tiingo world
- [x] 6.2 Full local pipeline run (`PipelineEndToEndIT`, `-Pe2e`) against the new MEXC-only active universe — green (107s), no `TIINGO_API_KEY`/Binance base-URL overrides set at all, proving Binance/Tiingo are genuinely dormant end-to-end
- [x] 6.3 Full test suite green (269 non-e2e + 1 e2e)
