## Why

Live ingestion runs produced 274 `duplicate key value violates unique constraint "unique_asset_timeframe_close"` errors on `candle` plus 30 more on `unique_supertrend_asset_timeframe_close`, across 183 of 236 assets, spanning both providers. Root-caused to two compounding bugs: (1) `IngestionRunDao`'s single-flight lock is keyed by `(provider, timeframe)`, but since `add-tiingo-provider` a single run already processes every active asset regardless of provider — so a `BINANCE`-labeled trigger and a `TIINGO`-labeled trigger for the same timeframe don't collide on that lock at all, letting two full pipeline runs execute concurrently over the same asset universe; (2) `CandleDao.upsert`/`SuperTrendIndicatorDao.upsert` do a non-atomic check-then-act (`SELECT` then `INSERT`/`UPDATE`), so when two concurrent runs both see "no row yet" for the same `(asset_id, timeframe, close_time)`, the loser gets a raw constraint-violation exception instead of converging safely.

Separately: two Binance symbols (`XMRUSDT`, `DAIUSDT`) silently ended up with zero candles and no logged error — a provider returning a valid-but-empty response (as opposed to an invalid-symbol error) during an expected fetch window is currently indistinguishable from "already up to date."

## What Changes

- **BREAKING**: `POST /api/v1/internal/ingestion/run` no longer accepts a `provider` field — a run always covers every provider's assets (matching what it's always actually done since `add-tiingo-provider`), so a per-provider trigger was a misleading, unsafe illusion of scoping. Body is now `{"timeframe": "D1"}` (optional, defaults to `D1`).
- Remove `provider` from `ingestion_run` entirely: the DB column, the unique single-flight index, the lookup index, `IngestionRunDao`'s method signatures, the `IngestionRun`/`IngestionRunOutcome` records' relevant call sites, `PipelineOrchestrator.runDaily`/`triggerAsync`, `DailyScheduler`, `FreshnessService.metadataFor`, and every caller that was hardcoding `Provider.BINANCE` into these calls (which was already silently wrong for Tiingo-only successful runs).
- Make `CandleDao.upsert` and `SuperTrendIndicatorDao.upsert` atomic via `INSERT ... ON CONFLICT ... DO UPDATE ... WHERE <values differ>`, removing the check-then-act race entirely (independently of the run-lock fix — this is the correct shape for these methods regardless).
- `CandleIngestionService.ingestForAsset` logs a warning when a fetch was actually attempted (window wasn't skipped as already-up-to-date) but the provider returned zero candles, instead of going silent.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
(none tracked as formal specs yet for `market-data-ingestion`'s run-locking/upsert behavior from the base `supertrend-core-java25-rewrite` change — captured here as delta requirements against that behavior instead, see specs/)

## Impact

- Schema: new migration dropping `ingestion_run.provider` and replacing its indexes.
- Public API: removes `provider` from the ingestion trigger request body (breaking, but this is an internal/ops endpoint, not a public integration surface).
- `IngestionRunDao`, `IngestionRun`, `PipelineOrchestrator`, `DailyScheduler`, `IngestionTriggerRequest`/`Handler`, `FreshnessService` and its 3 handler callers, `WeeklyTrendBriefingHandler`/`WeeklyPullbackBriefingHandler`, `CandleDao`, `SuperTrendIndicatorDao`, `CandleIngestionService`.
- No change to `Provider`/`AssetVenue` on `Asset` itself, or to which provider's client an asset's candles come from — this is purely about run-tracking/locking, not data routing.
