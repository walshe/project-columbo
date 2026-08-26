## Why

A real ingestion run (a full historical backfill, 30,494 candles inserted across 247 assets) produced widespread `HikariPool - Connection is not available ... (total=10, active=10, idle=0, waiting=192+)` failures during D1 SuperTrend/signal computation, affecting a broad mix of assets across both providers. Root cause: `IndicatorComputationService`/`SignalStateDetectionService` fan out to one virtual thread per active asset (unbounded, via `ParallelAssetExecutor`), and each asset's computation acquires a fresh pool connection **per individual DB statement** rather than reusing one connection for all of that asset's work. On a full backfill, one asset can need hundreds of upsert calls (one per historical day needing a new indicator/signal row); with 247 assets computing concurrently, that's tens of thousands of connection acquisitions competing for HikariCP's default 10-connection pool within its default 30-second timeout. This is a structural scalability issue, not an artifact of any one environment — it reproduced identically in a real deployment after appearing to be local-sandbox flakiness during earlier testing.

Separately: the active asset universe had grown to 247 (200 tokenized Binance STOCK/ETF/CRYPTO + 47 real Tiingo equities) since the original ~60-asset design point, directly amplifying the fan-out. The tokenized Binance STOCK/ETF assets are also now redundant with Tiingo's real equities for the same companies (see `add-tiingo-provider`), so trimming them is a real cleanup, not just a resource workaround.

## What Changes

- **BREAKING (data)**: retires Binance's tokenized STOCK/ETF asset class entirely (deactivates all of it) - Binance is now crypto-only, capped to the earliest 50 onboarded (by insertion order); Tiingo (already real-equity-only) is capped to 50 (currently 47, a no-op today, a guard against future growth). Leaves no ETF coverage until/unless Tiingo ETFs are onboarded separately - explicitly out of scope here.
- `IndicatorComputationService`, `SignalStateDetectionService`, and `ProvisionalTrendService` now acquire **one connection per asset** and reuse it for all of that asset's DB calls, instead of one connection per statement - removing the actual root cause of the pool exhaustion, independent of asset-count.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
(none tracked as formal specs yet for the base pipeline/asset-onboarding behavior from `supertrend-core-java25-rewrite`/`add-asset-category-filter` - captured here as delta requirements instead, see specs/)

## Impact

- Schema: new migration deactivating tokenized Binance STOCK/ETF assets and capping Binance CRYPTO / Tiingo to 50 each.
- `CandleDao`, `SuperTrendIndicatorDao`, `SignalStateDao`: new connection-accepting overloads alongside the existing ones (no removal, existing callers unaffected).
- `IndicatorComputationService`, `SignalStateDetectionService`, `ProvisionalTrendService`: constructor now also takes a `DataSource`; every call site (`Main.java` and tests) updated.
- `PipelineEndToEndIT`: FUTURES-venue-routing proof asset changes from `AAPLUSDT` (now deactivated) to `HYPEUSDT` (a genuinely FUTURES-only crypto asset, survives the crypto cap); asset-count assertions updated.
- No change to candle ingestion routing, SuperTrend math, or the atomic-upsert shape from `fix-ingestion-run-concurrency` - this is purely about connection lifecycle and asset-universe size.
