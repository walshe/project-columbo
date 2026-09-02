## 1. Shared warm-up constant

- [ ] 1.1 Expose `SuperTrendCalculator.WARMUP_WINDOW_BARS` (= `DEFAULT_ATR_LENGTH * 10`) and use it in place of the inline `atrLength * 10` inside `calculateIncremental`, so the DAO window and the calculator's internal slice provably use one value.

## 2. CandleDao — bounded reads

- [ ] 2.1 Add `findLatestCloseTime(Connection, long assetId, Timeframe)` overload; existing no-arg method delegates to it (matches the connection-accepting pattern from `fix-pipeline-connection-pool-exhaustion`).
- [ ] 2.2 Add `findWindowForIncremental(Connection, long assetId, Timeframe, OffsetDateTime anchorCloseTime, int warmupBars)` returning candles ordered oldest→newest, from `warmupBars` rows before the anchor onward, using the `COALESCE(... OFFSET ? LIMIT 1, '-infinity')` subquery from design.md §1. Fewer than `warmupBars` pre-anchor candles ⇒ returns full history.
- [ ] 2.3 `CandleDaoIntegrationTest` (or `AssetDaoIntegrationTest` sibling): cover `findWindowForIncremental` — exact window size when history is deep, full-history fallback when shallow, empty when no candles, boundary at the anchor row itself.
- [ ] 2.4 Verify the `findWindowForIncremental` query plan against a real DB with a large (multi-year daily) series — confirm index-only backward scan on `(asset_id, timeframe, close_time)`, no seq scan (project convention on non-routine SQL).

## 3. IndicatorComputationService

- [ ] 3.1 In `computeForAsset`: fetch `superTrendIndicatorDao.findLatestCloseTime(connection, ...)` and `candleDao.findLatestCloseTime(connection, ...)` first; return early when a stored value exists and the latest candle is not after it.
- [ ] 3.2 Replace the unbounded `candleDao.findByAssetAndTimeframe(connection, ...)` with `candleDao.findWindowForIncremental(connection, assetId, timeframe, lastStored, WARMUP_WINDOW_BARS)` when `lastStored` is present; keep the full fetch only for the no-anchor (first-run) path.
- [ ] 3.3 Keep the `calculateIncremental(..., lastStored, false)` call and the per-result upsert loop unchanged.
- [ ] 3.4 Optional DEBUG log per phase: `"D1 indicator: computed X, skipped Y (unchanged) of Z assets"`.

## 4. SignalStateDetectionService

- [ ] 4.1 In `computeForAsset`: fetch `signalStateDao.findLatestCloseTime(connection, ...)` and `candleDao.findLatestCloseTime(connection, ...)` first; return early when a stored row exists and the latest candle is not after it.
- [ ] 4.2 Replace the unbounded fetch with `candleDao.findWindowForIncremental(connection, assetId, timeframe, lastStored, WARMUP_WINDOW_BARS)` when `lastStored` is present; full fetch only when no row is stored.
- [ ] 4.3 Keep `calculator.calculate(window, ...)`, `detect(...)`, and the `closeTime` after `lastStored` write filter unchanged — `detect` still iterates the whole window so `previous` is established before the anchor.
- [ ] 4.4 Optional DEBUG log per phase mirroring 3.4.

## 5. Tests

- [ ] 5.1 `SuperTrendCalculatorTest`: full-series vs. bounded-window (`findWindowForIncremental`-shaped input) produce byte-identical `SuperTrendResult` for every candle at/after a mid-series anchor, on a long synthetic append-only series.
- [ ] 5.2 `IndicatorComputationServiceTest` / integration: unchanged asset ⇒ zero candle reads + zero upserts (spy/verify); asset with one new candle ⇒ exactly one new indicator row, identical to a full recompute; first-run asset ⇒ full history computed.
- [ ] 5.3 `SignalStateDetectionServiceIntegrationTest`: flip landing exactly on the first new candle is still detected via the bounded path; unchanged asset skipped; bounded vs. full detection agree on trend state + event for every post-anchor candle.
- [ ] 5.4 `PipelineOrchestratorTest`: a run where no asset got a new candle completes with no indicator/signal upserts and unchanged status semantics.
- [ ] 5.5 Full `mvn test` — no regressions.
- [ ] 5.6 `mvn verify -Pe2e` (`PipelineEndToEndIT`) — first run computes, an immediate second run with no new candles skips every asset and still passes all freshness/coverage assertions.

## 6. Verification

- [ ] 6.1 Self-review before opening the PR.
- [ ] 6.2 Check the `pr_agent` review comment before declaring the PR ready.
- [ ] 6.3 After deploy: confirm run duration for the D1/W1 indicator + signal phases drops materially on a steady-state run (one new candle per asset), and a re-run within the same day is near-instant for those phases.
