## Context

Both weekly briefings already run the full `PipelineOrchestrator.runDaily` pipeline (ingest -> D1/W1 indicators -> D1/W1 signals -> D1/W1 pulse) synchronously before composing their report, so the request is already expensive; this feature computes on top of already-fresh data within the same request, not on a separate schedule.

Two existing pure functions do almost all of the real work already:
- `CandleRollupService`'s private `groupByWeek`/`aggregate` methods turn a list of D1 candles into Monday-start weekly OHLCV aggregates - exactly the operation needed to synthesize a week-to-date candle, just without requiring exactly 7 days.
- `SuperTrendCalculator.calculate`/`calculateIncremental` are pure, stateless functions over a candle list with no DB coupling - `calculateIncremental(candles, atrLength, multiplier, lastStoredCloseTime, false)` already returns only the result(s) after a given close time, using a warm-up window rather than recomputing the whole history - exactly what's needed to get just the synthetic candle's result cheaply.

`CandleDao` only exposes per-asset queries for full candle history (`findByAssetAndTimeframe(assetId, timeframe)`) - there is no bulk "all assets' full history" query, and none should be added (would be a large, unnecessary result set). This constrains the new service to the same per-asset-loop-with-parallelism shape already used by `IndicatorComputationService`/`CandleRollupService`, not `SignalQueryService`'s bulk-then-join shape.

## Goals / Non-Goals

**Goals:**
- Compute, per active asset, a provisional W1 trend direction and flip-level price from this week's D1 candles ingested so far - fresh on every call, nothing persisted.
- Surface it in both weekly briefings per the rules in `proposal.md`, using only committed data for anything already relied on elsewhere (pulse counts, confluence, scan ranking).
- Keep the new computation cheap relative to the pipeline run each briefing already triggers.

**Non-Goals:**
- Provisional D1 (or any intraday) flip detection - would need new intraday ingestion (multiple daily polls, or 4h/1h candles), a different capability.
- Any persistence of the provisional read, or any new `TrendState`/`SignalEvent` value.
- Changing how the committed `/signals`, `/summary`, `/summary/trend-alignment`, or `/scan` endpoints behave - this change only touches the two weekly briefings and the new service backing them.

## Decisions

**Extract `CandleRollupService`'s week-grouping/aggregation into a small shared utility rather than duplicating it.** The new service needs the identical "group D1 candles into Monday-start weeks, aggregate a week's candles into one OHLCV candle" logic `CandleRollupService` already has, just applied to an in-progress week instead of only complete ones. Duplicating ~15 lines into the new service risks the two diverging if the aggregation rule ever changes (e.g. a different rollup rule for markets with holiday-shortened weeks). Widening `CandleRollupService`'s two private static methods to package-visible (or extracting them into a tiny new `rollup`-package utility, e.g. `WeeklyCandleAggregation`) keeps there being exactly one implementation. *Alternative considered:* duplicate the ~15 lines directly in the new service - rejected, same reasoning that motivated extracting `WeeklyBriefingFormatting`/`WeeklyBriefingSignals` earlier in this codebase: don't wait for a third copy to justify sharing two.

**New service (`ProvisionalTrendService`) lives in the `signal` package**, not `rollup` or `indicator` - it produces a trend-state-shaped read consumed by the signals/reporting layer, the same role `TrendAlignmentService`/`ScanService` already play. It depends on `CandleDao` (read-only) and the pure `SuperTrendCalculator` - no DAO writes, no dependency on `SignalStateDao` for computing the read itself (only the caller needs `SignalStateDao`, to compare against the committed state).

**Per-asset loop with `ParallelAssetExecutor`, not a bulk query.** Computing SuperTrend needs each asset's full D1 (for warm-up) and W1 (for the incremental append) candle history individually - there is no bulk shortcut, and none should be invented just for this. This mirrors `IndicatorComputationService.computeForAllActiveAssets`/`CandleRollupService.rollupForAllActiveAssets` exactly: `AssetDao.findAllActive(assetClassFilter)`, then `ParallelAssetExecutor.runForEachItem`, one asset's failure caught and logged without affecting others. Returns a `Map<String symbol, ProvisionalTrendResult>` (or empty entry when there's nothing to synthesize yet).

**Reuse `SuperTrendCalculator.calculateIncremental`, not `calculate` + manual tail-taking.** `calculateIncremental(candles, atrLength, multiplier, lastStoredCloseTime, false)` already exists for exactly this shape of problem (existing committed history, want only the result(s) after a known point) and is already used by `IndicatorComputationService`. Append the synthetic week-to-date candle to the asset's full committed W1 candle list, pass the last committed W1 candle's close time as `lastStoredCloseTime`, and the single returned result is the provisional read (`superTrend` = flip-level price, `direction` = UP/DOWN, mapped to `TrendState` the same way `SignalStateDetectionService` already maps it).

**Divergence comparison lives in the caller (handler/formatter), not the service.** `ProvisionalTrendService` only answers "what is the provisional read"; "is this different from the committed state, and therefore worth showing" is a presentation-layer judgment made once per report using data (`SignalQueryService`/`SignalStateDao`) the handlers already have. Keeps the service focused and reusable if a future consumer wants the raw read without the divergence framing.

## Risks / Trade-offs

- **[Risk] Early-week provisional reads are noisy.** On a Monday, the week-to-date candle is one day of data - both direction and flip-level can legitimately swing through the week as more days arrive, since ATR/bands depend on the still-accumulating high/low. -> **Mitigation:** label it "provisional, as of today" everywhere it's shown (never presented as settled), matching how the reference tool in the screenshot frames it; no code-level fix needed, this is inherent to the concept and should be communicated, not hidden.
- **[Risk] Added latency per weekly-briefing request.** Computing SuperTrend for ~200 assets is real work on top of the pipeline run the briefing already triggers. -> **Mitigation:** reuses `calculateIncremental`'s warm-up-window optimization (not full history recompute) and the existing `ParallelAssetExecutor` virtual-thread parallelism; if this turns out to matter in practice, it's small and isolated enough to optimize later without touching the public shape.
- **[Risk] "Flips Forming" section requires computing provisional reads for every active asset in a class**, not just ones already surfaced by existing confluence/scan queries - a materially larger candidate set than what the trend briefing currently touches. -> **Mitigation:** this is inherent to the requirement (previewing names not yet confluence-eligible means considering the full universe); accepted as the cost of the feature, revisit only if it proves too slow in practice.

## Migration Plan

No data migration - no schema change. New code is purely additive (a new service, new report sections, one extended shared section); nothing existing changes shape. Safe to roll back by reverting the branch/PR with no cleanup required.

## Open Questions

- Exact section/field wording in each report's Markdown output - left to implementation, no spec-level constraint beyond "separate section" (trend briefing) vs "inline annotation" (pullback briefing).
