## Context

Every DAO in this codebase opens its own JDBC connection per method call (`try (Connection connection = dataSource.getConnection())`), a pattern that's been consistent since `supertrend-core-java25-rewrite`. This is harmless when calls are infrequent relative to the pool size. `IndicatorComputationService.computeForAsset`/`SignalStateDetectionService.computeForAsset` loop over every day needing a new persisted value and call `upsert()` once per day - fine for an incremental daily run (1 new day per asset ≈ 2-3 total connection acquisitions), but on a full backfill (many historical days per asset, all needing individual upserts) this multiplies per-asset connection acquisitions by however many days are being caught up. Combined with `ParallelAssetExecutor`'s unbounded one-virtual-thread-per-asset fan-out (247 assets = 247 concurrent threads, each independently hammering the pool), this produces a genuine thundering herd against HikariCP's default 10-connection pool and 30-second acquisition timeout - confirmed reproducing identically in a real deployment, not just a resource-constrained local sandbox.

Separately, the 247-asset universe itself (200 Binance-tokenized + 47 Tiingo) is larger than the pipeline was originally sized for, and half of it (the tokenized STOCK/ETF batch) is redundant now that Tiingo carries real equities for the same companies.

## Goals / Non-Goals

**Goals:**
- Per-asset computation acquires a bounded, small number of connections (ideally one) regardless of how many historical days it needs to catch up - eliminates the actual mechanism that caused the pool exhaustion, not just its current trigger (backfill scale).
- Reduce the concurrent fan-out size by retiring redundant assets, as a complementary mitigation (fewer concurrent threads = less pool pressure even before the connection-reuse fix, and less redundant computation regardless).

**Non-Goals:**
- No change to HikariCP's pool size or timeout configuration - the connection-reuse fix addresses the actual cause; tuning pool size would only raise the threshold at which the same class of problem reappears at larger scale.
- No change to `ParallelAssetExecutor`'s unbounded-fan-out design - bounding concurrency (e.g. a semaphore) was considered but is unnecessary once each asset's connection footprint is O(1) instead of O(days); revisit only if profiling shows fan-out itself (not connections) is a bottleneck.
- No onboarding of Tiingo ETF assets to fill the coverage gap left by retiring Binance's tokenized ETFs - a distinct scope (new symbol research, live verification, mirroring `add-tiingo-provider`'s own process) deferred to a future change if wanted.
- No change to `CandleIngestionService` - it's already sequential (not parallelized across assets, see `add-tiingo-provider`'s notes), so it was never subject to this specific thundering-herd mechanism even though it also loops per-day per asset.

## Decisions

**1. Reuse one connection per asset (acquired at the top of `computeForAsset`, passed through to every DAO call for that asset) rather than raising the pool size.**
This is the actual root-cause fix: it caps connection acquisitions per asset at 1 regardless of how many days of history need catching up, so the fan-out (however many assets are active) no longer multiplies against per-asset statement count. Raising `maximumPoolSize` alone would only push the failure threshold higher without fixing the underlying O(days) connection churn - the same class of failure would reappear on a big-enough backfill or a big-enough asset universe.
- *Alternative considered:* raise `HikariConfig.maximumPoolSize` (e.g. to 30-50). Rejected as the primary fix (though not mutually exclusive with the connection-reuse fix) - it papers over the actual inefficiency rather than removing it, and picking the "right" number requires guessing at future backfill/asset-count scale rather than fixing the O(days)-per-asset behavior that scales badly regardless of pool size.
- *Alternative considered:* bound `ParallelAssetExecutor`'s concurrency to roughly the pool size (e.g. a semaphore permitting only N assets in flight at once). Rejected as the primary fix for the same reason - it reduces concurrent pool pressure but doesn't fix that a single asset's backfill can still hold one connection for a very long time (however many sequential upserts it needs), and combines awkwardly with the existing "one asset's failure doesn't affect others" isolation model (a semaphore-blocked asset waiting behind slow ones changes that isolation's timing characteristics for no benefit once the real fix is in place).

**2. Add connection-accepting overloads to the DAOs rather than replacing the existing no-argument methods.**
Every other caller of these DAOs (API handlers, `SignalQueryService`, `ScanService`, one-off/test call sites) makes a single call per request and has no asset-loop to share a connection across - forcing them to also manage a `Connection` would be pure ceremony with no benefit. The existing methods become thin wrappers that acquire a connection and delegate to the new overload.
- *Alternative considered:* a request/unit-of-work-scoped `Connection` threaded through every layer via a shared context object. Rejected as much larger surgery than this problem needs - only three services have the O(days)-per-asset loop shape that actually causes pool pressure.

**3. Retire Binance's tokenized STOCK/ETF asset class entirely, cap Binance to crypto and Tiingo to real equities.**
Direct instruction, and also completes a direction already recorded as intended (tokenized stock/ETF proxies were always meant to be superseded by real exchange data once a real-data provider existed - see `project_stock_data_source_reconsideration` history). Capping both providers to 50 each keeps the active universe modest and roughly balanced, rather than one provider dominating fan-out.
- *Alternative considered:* keep tokenized STOCK/ETF active but deprioritize/exclude it from the parallel-heavy phases only. Rejected - it's genuinely redundant data now that Tiingo covers real equities for the same companies, and keeping duplicate, unreconciled trend signals for the same underlying company (one synthetic, one real) was already flagged as a known trade-off in `add-tiingo-provider`, not something to entrench further.

## Risks / Trade-offs

- **[Trade-off] No ETF coverage until Tiingo ETFs are onboarded separately.** → Accepted, explicit scope boundary - flagged to the user, not a silent gap.
- **[Risk] Deactivating ~150 Binance assets is a large data change.** → Mitigated: deactivation (not deletion), matching this project's established self-heal-via-active convention (`V14`) - fully reversible, preserves historical candle/indicator data for the deactivated assets rather than orphaning it.
- **[Risk] `PipelineEndToEndIT`'s FUTURES-venue-routing proof asset (`AAPLUSDT`) is itself deactivated by this change.** → Mitigated: repointed at `HYPEUSDT`, one of the few genuinely FUTURES-only CRYPTO-class assets (V14), which survives the crypto cap and proves the identical thing (FUTURES-venue candles actually flow through `/fapi/v1/klines`).

## Migration Plan

1. `V22__cap_binance_crypto_tiingo_stocks.sql`: deactivate all Binance `STOCK`/`ETF` assets; cap Binance `CRYPTO` and Tiingo to the earliest 50 each by id.
2. Code deploy: connection-reuse changes to `IndicatorComputationService`/`SignalStateDetectionService`/`ProvisionalTrendService` and their DAOs, plus `Main.java`'s updated wiring.
3. No ordering constraint between the two - a plain data migration and a connection-lifecycle code change are independent; either deploy order is safe.
- **Rollback:** reactivate the deactivated rows (`UPDATE asset SET active = true WHERE ...`) if ever needed - no schema change to reverse, matching the existing self-heal-via-active pattern.
