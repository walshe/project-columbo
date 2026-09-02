## Context

`IndicatorComputationService.computeForAsset` and `SignalStateDetectionService.computeForAsset` both:

1. call `candleDao.findByAssetAndTimeframe(connection, assetId, timeframe)` — an unbounded `SELECT ... ORDER BY close_time ASC` over the asset's whole candle history for that timeframe;
2. run the SuperTrend calculator over that full series (`calculateIncremental` in the indicator service — which internally still calls the full `calculate` and then slices; `calculate` directly in the signal service);
3. upsert only rows after the last stored `close_time` (indicator) / filter the same way before `signalStateDao.upsert` (signal state).

Step 3 is already incremental. Steps 1–2 are not. Run cost is therefore O(total history × active assets) every run, regardless of how little changed. `SuperTrendCalculator.calculateIncremental`'s own Javadoc already establishes the correctness basis for doing better: a warm-up window of `atrLength * 10` candles before the anchor is sufficient for Wilder ATR and the band/direction recurrence to restabilize to values byte-identical to a full recompute. That window is currently applied only to *slice the output* — the input is still the whole history.

Unfinalized candles are already excluded upstream: `CandleIngestionService` caps its fetch `endTime` at `FinalizedBoundary.utcMidnightToday(...)`, so the `candle` table never contains a partial candle. Neither service needs to re-filter for finalization.

Constraints:
- SuperTrend is a recurrence (sticky bands, Wilder ATR, previous-bar flip timing per `supertrend-calculation`). A candle cannot be computed in isolation — it needs a warmed-up predecessor state. The warm-up window is how that state is reconstructed without loading everything.
- Per-asset work runs on one virtual thread each and shares a single connection per asset (`fix-pipeline-connection-pool-exhaustion`). This change must not regress that — no extra connections, no per-statement acquisition.
- Phase ordering is fixed and each phase commits before the next (`pipeline-orchestration`): ingestion → D1 indicator → D1 signal → … The signal phase reads the indicator table? No — it recomputes SuperTrend in memory from candles. So the two phases are independent recomputes of the same series from the same source.

## Goals / Non-Goals

**Goals:**
- Per-asset, per-run candle reads and calculator work are O(new candles + fixed warm-up window), not O(total history).
- An asset with no new finalized candle since its last stored indicator/signal row does zero work — no candle fetch, no recompute, no upsert calls.
- Bit-for-bit identical stored results vs. the current full-history recompute, for every candle at and after each asset's anchor, on an append-only candle history.
- A retained full-recompute path for backfills / formula changes / detected gaps.

**Non-Goals:**
- No merge of the indicator and signal phases into one pass (evaluated below, deferred).
- No change to `SuperTrendCalculator`'s arithmetic, band logic, or flip timing.
- No automatic gap/backfill detection. Bounded incremental assumes append-only candle arrival; a deliberate historical backfill must still be followed by a full recompute, exactly as `IndicatorComputationService` already implicitly assumes today via `calculateIncremental`.
- No change to `ProvisionalTrendService` (unpersisted "as of today" read, no anchor, not on this hot path).
- No change to run-record counts (`IngestionStats` is populated by the ingestion phase only; the indicator/signal phases already just log).
- No new index (the existing `(asset_id, timeframe, close_time)` key and `idx_candle_timeframe_close` cover the bounded range scan).

## Decisions

### 1. Bound the candle fetch with a precise "N bars before the anchor" window, computed in SQL — not a calendar-time heuristic

New `CandleDao` method, connection-accepting (matching the established overload pattern):

```
List<Candle> findWindowForIncremental(Connection c, long assetId, Timeframe tf,
                                      OffsetDateTime anchorCloseTime, int warmupBars)
```

```sql
SELECT open_time, close_time, open, high, low, close, volume
FROM candle
WHERE asset_id = ? AND timeframe = ?::timeframe
  AND close_time >= COALESCE(
        (SELECT close_time FROM candle
         WHERE asset_id = ? AND timeframe = ?::timeframe AND close_time < ?
         ORDER BY close_time DESC
         OFFSET ? LIMIT 1),
        '-infinity'::timestamptz)
ORDER BY close_time ASC
```

`OFFSET = warmupBars` (the (warmupBars+1)-th row back). If fewer than `warmupBars` candles precede the anchor, the subquery yields nothing, `COALESCE` falls to `-infinity`, and the whole history is returned — correct, because there isn't enough history to bound and a full recompute over what exists is what the current code does anyway.

`warmupBars = atrLength * 10` (100 with the default `atrLength = 10`), matching `calculateIncremental`'s existing internal constant. Callers pass `SuperTrendCalculator.DEFAULT_ATR_LENGTH * 10` (or a small exposed constant) so the two stay coupled.

- *Alternative considered: fetch `close_time >= anchor - <interval>` with a per-timeframe interval (e.g. 250 days for D1, 3 years for W1).* Rejected — it bakes in an assumption about bar spacing (trading-day gaps, holidays, listing date) that is wrong often enough to either over-fetch badly or, worse, under-fetch and silently change a stored value. The subquery is exact and cheap (index-only backward scan, `LIMIT 1`).
- *Alternative considered: add a row-number column / keyset and fetch by ordinal.* Rejected — `candle` has no stable ordinal; `close_time` is already the key and is monotonic per asset/timeframe.

### 2. Skip an asset before doing any work when it has no new finalized candle

At the top of `computeForAsset`, on the shared connection:

```
Optional<OffsetDateTime> lastStored = <indicator|signalState>Dao.findLatestCloseTime(c, assetId, tf);
Optional<OffsetDateTime> latestCandle = candleDao.findLatestCloseTime(c, assetId, tf);   // new conn-accepting overload
if (lastStored.isPresent()
        && latestCandle.isPresent()
        && !latestCandle.get().isAfter(lastStored.get())) {
    return;   // nothing new since last run
}
```

Two `MAX(close_time)` lookups (index-only) replace one full table scan + full recompute. `lastStored` empty → first run for this asset/timeframe → fall through to full compute. `latestCandle` empty → asset has no candles at all → existing `candles.isEmpty()` guard still applies after the (skipped) fetch; the skip check just returns early in that case too.

The indicator service already fetches `lastStored` (for `calculateIncremental`) — this reuses it. The signal service will now fetch its own `lastStored` up front instead of after the recompute.

- *Alternative considered: have the ingestion phase publish the set of asset ids that received a new candle, and iterate only those.* Rejected for this change — it couples the phases through a new shared structure and a `ParallelAssetExecutor` signature change, for a saving the two `MAX` queries already capture. Worth revisiting only if the per-asset skip check itself shows up in profiling.
- *Note:* this naturally preserves the "errored asset catches up next run" property — a failed asset never advanced its stored `close_time`, so `latestCandle > lastStored` stays true and it is reprocessed.

### 3. Signal-state detection: same bounded window, derive the pre-anchor trend state from the warm-up portion

`SignalStateDetectionService.computeForAsset` becomes:

1. skip check (Decision 2) using `signalStateDao.findLatestCloseTime`;
2. `window = candleDao.findWindowForIncremental(c, assetId, tf, anchor, warmupBars)` where `anchor = lastStored` (or full history when `lastStored` is empty);
3. `results = calculator.calculate(window, ...)` — unchanged call, just a shorter list;
4. `states = detect(assetId, tf, window, results)` — unchanged; `detect` iterates the whole window so `previous` is a fully-established `BULLISH`/`BEARISH` by the time iteration reaches candles at/after the anchor (the warm-up window is 10× the ATR warm-up);
5. persist only `states` with `closeTime` after `lastStored` — unchanged filter.

The first persisted candle is the one immediately after the anchor, and its `previous` is the trend state *at* the anchor, computed within the window — so a flip landing exactly on the first new candle is still detected. This is the same correctness argument `calculateIncremental` already relies on, applied to the signal pass.

- *Alternative considered: persist the calculator's carry state (`prevUp`, `prevDn`, `prevDirection`, `prevClose`, last ATR) next to the latest row and resume with zero warm-up.* Rejected for this change — genuinely O(new candles) with no window at all, but it makes stored state a migration concern on every formula tweak (and this project tweaks the formula — `update-supertrend-settings-and-flip-timing`), needs a full-recompute fallback path anyway, and the bounded-window approach already removes the full-history scan. Revisit only if the ~100-row window read is measurably too expensive.

### 4. Do not merge the indicator and signal phases

Once each pass reads only ~100 warm-up rows + new candles and skips untouched assets entirely, the only thing a merge saves is one small windowed `SELECT` and one calculator pass (microseconds) per changed asset per run. Against that: the two are currently clean, independently-tested phases with a committed boundary between them (`pipeline-orchestration`: "signal detection sees committed indicator writes"); merging couples them, complicates the "one asset's failure doesn't affect others" isolation, and means a change to either recompute touches the other. Not worth it now.

- *Revisit if:* profiling after this change still shows the indicator+signal phases dominating run time, or a future indicator needs the exact same series and a third redundant pass appears.

## Risks / Trade-offs

- **[Risk] A late-arriving historical candle (older than the anchor) is silently not reflected in stored indicator/signal values.** → This blind spot already exists in `IndicatorComputationService` today (it trusts `calculateIncremental`'s anchor). This change extends it to the signal pass and makes the assumption explicit in the spec: bounded incremental requires append-only candle arrival; deliberate backfills are followed by a full recompute (`fullRecalc = true` / signal-state equivalent). Mitigation: keep the full-recompute path first-class and documented; consider a follow-up change for automatic gap detection if backfills become routine.
- **[Risk] `warmupBars` too small → a bounded result diverges from a full recompute.** → `atrLength * 10` is the constant `calculateIncremental` has used in production since the rewrite; this change doesn't change it, only applies it to the read. Mitigation: a test that computes a long synthetic series both ways (full vs. bounded-window incremental) and asserts byte-identical `SuperTrendResult` / `SignalState` for every candle at/after a mid-series anchor.
- **[Trade-off] Two extra `MAX(close_time)` queries per asset per run (the skip check).** → Both are index-only and replace a full history scan + full recompute; net strongly positive. For an unchanged asset it's 2 tiny reads instead of thousands of rows + a full calculator run.
- **[Risk] `findWindowForIncremental`'s correlated subquery underperforms.** → It's an index-ordered backward scan with `OFFSET n LIMIT 1` on `(asset_id, timeframe, close_time)` — the same index the main query uses. Verify the plan against a real DB with a large series before merge (per project convention on non-routine SQL).
- **[Trade-off] The unbounded `findByAssetAndTimeframe` stays in `CandleDao`.** → Still used by rollup, market-pulse, and read APIs that genuinely need the whole series; not removed, just no longer used by the two hot-path incremental services.

## Migration Plan

No schema migration. Pure code change to two services and one new `CandleDao` method (plus a `findLatestCloseTime` connection-accepting overload if not already present).

1. Add `CandleDao.findWindowForIncremental` + the `findLatestCloseTime(Connection, …)` overload.
2. Add the skip check and swap the fetch in `IndicatorComputationService`.
3. Same in `SignalStateDetectionService`.
4. Deploy. First run after deploy: every asset's `lastStored` is already populated from prior runs, so every asset immediately uses the bounded path; unchanged assets are skipped. No warm-up or backfill needed.

**Rollback:** revert the code — the next run recomputes over full history again. No data to undo (stored values are identical either way on an append-only history).

## Open Questions

- Should `warmupBars` be a shared named constant surfaced from `SuperTrendCalculator` (so the DAO window and the calculator's internal slice provably use one value), rather than each computing `atrLength * 10` independently? Leaning yes — expose `SuperTrendCalculator.WARMUP_WINDOW_BARS` and use it in both places.
- Is a lightweight DEBUG-level "skipped N/total assets (unchanged)" log line per phase worth adding for operational visibility, given run-record counts stay ingestion-only? Leaning yes, one line per phase.
