## 1. Shared warm-up constant

- [x] 1.1 Expose `SuperTrendCalculator.WARMUP_WINDOW_BARS` (= `DEFAULT_ATR_LENGTH * 10`) and use it in place of the inline `atrLength * 10` inside `calculateIncremental`, so the DAO window and the calculator's internal slice provably use one value.

## 2. CandleDao — bounded reads

- [x] 2.1 Add `findLatestCloseTime(Connection, long assetId, Timeframe)` overload; existing no-arg method delegates to it (matches the connection-accepting pattern from `fix-pipeline-connection-pool-exhaustion`).
- [x] 2.2 Add `findWindowForIncremental(Connection, long assetId, Timeframe, OffsetDateTime anchorCloseTime, int warmupBars)` returning candles ordered oldest→newest, from `warmupBars` rows before the anchor onward, using the `COALESCE(... OFFSET ? LIMIT 1, '-infinity')` subquery from design.md §1. Fewer than `warmupBars` pre-anchor candles ⇒ returns full history.
- [x] 2.3 `CandleDaoIntegrationTest` (or `AssetDaoIntegrationTest` sibling): cover `findWindowForIncremental` — exact window size when history is deep, full-history fallback when shallow, empty when no candles, boundary at the anchor row itself.
- [x] 2.4 Verify the `findWindowForIncremental` query plan against a real DB with a large (multi-year daily) series — confirm index-only backward scan on `(asset_id, timeframe, close_time)`, no seq scan (project convention on non-routine SQL). Done: `EXPLAIN (ANALYZE, BUFFERS)` on Postgres 16 with a 3201-row/asset D1 series (2 assets) — anchor subquery = `Index Only Scan Backward using unique_asset_timeframe_close` (the `(asset_id, timeframe, close_time)` index), range = `Bitmap Index Scan` on the same index + tiny sort of ~230 rows; 17 shared buffer hits, 0.56ms, **no sequential scan**. Plan shape is bounded by the index cond, so it stays flat as history grows.

## 3. IndicatorComputationService

- [x] 3.1 In `computeForAsset`: fetch `superTrendIndicatorDao.findLatestCloseTime(connection, ...)` and `candleDao.findLatestCloseTime(connection, ...)` first; return early when a stored value exists and the latest candle is not after it.
- [x] 3.2 Replace the unbounded `candleDao.findByAssetAndTimeframe(connection, ...)` with `candleDao.findWindowForIncremental(connection, assetId, timeframe, lastStored, WARMUP_WINDOW_BARS)` when `lastStored` is present; keep the full fetch only for the no-anchor (first-run) path.
- [x] 3.3 Keep the `calculateIncremental(..., lastStored, false)` call and the per-result upsert loop unchanged.
- [x] 3.4 Optional DEBUG log per phase: `"D1 indicator: computed X, skipped Y (unchanged) of Z assets"`.

## 4. SignalStateDetectionService

- [x] 4.1 In `computeForAsset`: fetch `signalStateDao.findLatestCloseTime(connection, ...)` and `candleDao.findLatestCloseTime(connection, ...)` first; return early when a stored row exists and the latest candle is not after it.
- [x] 4.2 Replace the unbounded fetch with `candleDao.findWindowForIncremental(connection, assetId, timeframe, lastStored, WARMUP_WINDOW_BARS)` when `lastStored` is present; full fetch only when no row is stored.
- [x] 4.3 Keep `calculator.calculate(window, ...)`, `detect(...)`, and the `closeTime` after `lastStored` write filter unchanged — `detect` still iterates the whole window so `previous` is established before the anchor.
- [x] 4.4 Optional DEBUG log per phase mirroring 3.4.

## 5. Tests

- [x] 5.1 `SuperTrendCalculatorTest` (300-bar multi-flip wave, real defaults): (a) `calculateIncremental` over a pre-bounded window == over full history, byte-identical — the indicator-path guarantee; (b) `calculate` over the bounded window yields identical `direction` for every candle at/after a mid-series anchor — the signal-path guarantee (ATR/band magnitudes carry a sub-1e-4 EMA residue and are not compared; not persisted by signal state).
- [x] 5.2 `IndicatorComputationServiceTest`: unchanged asset ⇒ `SKIPPED` outcome (no fetch/recompute/upsert); first-run asset ⇒ `COMPUTED` full history; no-candles asset ⇒ `NO_CANDLES`; one new candle on deep history ⇒ stored row byte-identical to the unbounded-fetch path (`calculateIncremental` over full history at the same anchor). Project has no mocking framework, so the outcome enum is the observable rather than spy/verify.
- [x] 5.3 `SignalStateDetectionServiceIntegrationTest`: flip landing exactly on the first new candle is still detected via the bounded path; unchanged asset skipped; bounded vs. full detection agree on trend state + event for every post-anchor candle.
- [x] 5.4 `PipelineOrchestratorTest`: a run where no asset got a new candle completes with no indicator/signal upserts and unchanged status semantics.
- [x] 5.5 Full `mvn test` — no regressions.
- [x] 5.6 `mvn verify -Pe2e` (`PipelineEndToEndIT`) green (166s) — full pipeline unaffected end-to-end. The explicit "second run with no new candles skips every asset" assertion lives in `PipelineOrchestratorTest` (@Order 7) rather than the single-run e2e.

## 6. Verification

- [x] 6.1 Self-review before opening the PR.
- [ ] 6.2 Check the `pr_agent` review comment before declaring the PR ready.
- [ ] 6.3 After deploy: confirm run duration for the D1/W1 indicator + signal phases drops materially on a steady-state run (one new candle per asset), and a re-run within the same day is near-instant for those phases.
