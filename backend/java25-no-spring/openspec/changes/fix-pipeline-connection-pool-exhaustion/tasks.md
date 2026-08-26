## 1. Schema

- [x] 1.1 Add `V22__cap_binance_crypto_tiingo_stocks.sql`: deactivate all `provider = 'BINANCE' AND asset_class IN ('STOCK','ETF')`; cap `provider = 'BINANCE' AND asset_class = 'CRYPTO'` and `provider = 'TIINGO'` each to the earliest 50 by id, deactivating the rest.

## 2. Persistence (connection-accepting overloads)

- [x] 2.1 `CandleDao.findByAssetAndTimeframe`: add a `Connection`-accepting overload; existing method delegates to it.
- [x] 2.2 `SuperTrendIndicatorDao.findLatestCloseTime` / `upsert`: same treatment.
- [x] 2.3 `SignalStateDao.findLatestCloseTime` / `upsert`: same treatment.

## 3. Services (connection reuse)

- [x] 3.1 `IndicatorComputationService`: constructor takes a `DataSource`; `computeForAsset` acquires one connection per asset and passes it to every DAO call for that asset.
- [x] 3.2 `SignalStateDetectionService`: same treatment.
- [x] 3.3 `ProvisionalTrendService`: same treatment (lower urgency - read-only, no per-day upsert loop - but cheap once the pattern exists, and it's on the same parallel-per-asset fan-out).
- [x] 3.4 `Main.java`: update all three constructor calls to pass the shared `DataSource`.
- [x] 3.5 Update every test constructing these three services (`PipelineOrchestratorTest`, `IndicatorComputationServiceTest`, `SignalStateDetectionServiceIntegrationTest`, `OpenApiDocumentationIntegrationTest`, `IngestionTriggerHandlerIntegrationTest`) to pass a `DataSource`.

## 4. Test updates for the asset cap

- [x] 4.1 `PersistenceIntegrationTest.assetDaoFindsAllSeededActiveAssets`: update expected active-asset count (247 → 97: 50 capped crypto + 47 Tiingo).
- [x] 4.2 `PipelineEndToEndIT`: repoint `FUTURES_SYMBOL` from `AAPLUSDT` (now deactivated) to `HYPEUSDT` (a genuinely FUTURES-only CRYPTO asset that survives the cap); update `assetCount` assertions (246 → 96, accounting for the 1 invalid-symbol deactivation during the test run).

## 5. Verification

- [x] 5.1 Run full `mvn test` suite, confirm no regressions. Passed: 258 tests.
- [x] 5.2 Run `mvn verify -Pe2e`, confirm no regressions. Passed: `PipelineEndToEndIT` in 213s (normal runtime) with the `HYPEUSDT` FUTURES-venue assertion and 96-asset coverage counts, no HikariPool errors.
- [x] 5.3 Self-review before opening the PR.
- [ ] 5.4 After deploy: confirm a full backfill run no longer produces `HikariPool` connection-timeout errors.
