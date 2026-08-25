## 1. Schema

- [x] 1.1 Add `V20__drop_ingestion_run_provider.sql`: drop `idx_ingestion_run_running` and `idx_ingestion_run_lookup`, drop `ingestion_run.provider`, create `idx_ingestion_run_running` as a unique index on `(timeframe) WHERE status = 'RUNNING'`, create `idx_ingestion_run_lookup` on `(timeframe, started_at DESC)`.

## 2. Persistence

- [x] 2.1 `IngestionRunDao`: drop `provider` from `isRunning`, `start`, `findLatestSuccessfulFinishedAt`, the INSERT/SELECT SQL, and `mapRow`.
- [x] 2.2 `IngestionRun` record: drop the `provider` field.
- [x] 2.3 `CandleDao.upsert`: rewrite as a single atomic `INSERT ... ON CONFLICT (asset_id, timeframe, close_time) DO UPDATE ... WHERE <differs> RETURNING (xmax = 0)`; classify `INSERTED`/`UPDATED`/`UNCHANGED` from the result instead of a prior `SELECT`.
- [x] 2.4 `SuperTrendIndicatorDao.upsert`: same atomic rewrite.

## 3. Pipeline / scheduling

- [x] 3.1 `PipelineOrchestrator.runDaily`/`triggerAsync`/`start`: drop the `Provider` parameter; update the "Pipeline run {} started" log line.
- [x] 3.2 `IngestionAlreadyRunningException`: drop the `Provider` parameter; update its message.
- [x] 3.3 `DailyScheduler`: drop the `Provider` constructor parameter and its use; update the "Scheduled daily trigger firing" log line.
- [x] 3.4 `Main.java`: update the `DailyScheduler` construction call to drop `Provider.BINANCE`.

## 4. API

- [x] 4.1 `IngestionTriggerRequest`: drop the `provider` field, keep only `timeframe` (defaults to `D1`).
- [x] 4.2 `IngestionTriggerHandler`: drop `request.provider()` from the `triggerAsync` call; update its `@OpenApi` summary/description/409-response text to no longer mention provider scoping.
- [x] 4.3 `WeeklyTrendBriefingHandler`/`WeeklyPullbackBriefingHandler`: drop `Provider.BINANCE` from their `runDaily` calls.

## 5. Freshness

- [x] 5.1 `FreshnessService.metadataFor` (both overloads): drop the `Provider` parameter; update `findLatestSuccessfulFinishedAt` call.
- [x] 5.2 `TrendAlignmentHandler`/`SignalsHandler`/`SummaryHandler`: drop `Provider.BINANCE` from their `metadataFor` calls; drop the now-unused `Provider` import from each.

## 6. Ingestion logging

- [x] 6.1 `CandleIngestionService.ingestForAsset`: log a WARN when `provider.fetchDailyCandles(...)` returns an empty list after a fetch was actually attempted (i.e. the debug-level "no new candles required" skip didn't fire).

## 7. Test updates

- [x] 7.1 `PipelineOrchestratorTest`: drop `Provider.BINANCE` from every `runDaily`/`triggerAsync`/`ingestionRunDao.start` call; update the `(provider, timeframe)` comment about the unique partial index.
- [x] 7.2 `IngestionTriggerHandlerIntegrationTest`: drop `provider` from the JSON body in `explicitProviderAndTimeframeAreHonored` (rename to reflect timeframe-only); drop `.provider()` assertion; update `ingestionRunDao.start` call.
- [x] 7.3 `FreshnessServiceIntegrationTest`: drop `Provider.BINANCE` from `metadataFor`/`ingestionRunDao.start` calls.
- [x] 7.4 `PersistenceIntegrationTest.ingestionRunDaoLifecycle`: drop `Provider.BINANCE` from `isRunning`/`start` calls.
- [x] 7.5 Add a `CandleDaoTest`/`SuperTrendIndicatorDaoTest` (or extend an existing integration test) proving two concurrent `upsert` calls for the same new `(asset, timeframe, close_time)` both complete without throwing and converge to one stored row.
- [x] 7.6 Add a `CandleIngestionServiceTest` case proving a provider returning an empty list during an expected fetch window logs a warning (or at minimum doesn't silently look identical to the already-caught-up skip path — assert via stats/behavior if log assertion isn't practical).

## 8. Docs

- [ ] 8.1 `README.md`: update the `/internal/ingestion/run` endpoint row to drop the `provider` field and the "same provider+timeframe" 409 wording.
- [ ] 8.2 `developer-notes.md`: document the removal of provider-scoped run tracking and why (link back to this change), so a future reader doesn't reintroduce a provider-scoped trigger.

## 9. Verification

- [ ] 9.1 Run full `mvn test` suite, confirm no regressions.
- [ ] 9.2 Run `mvn verify -Pe2e`, confirm no regressions.
- [ ] 9.3 Self-review before opening the PR.
