## Context

`SignalStateDto` is the shared result type returned by `SignalQueryService.listSignals`, and is used by every signal-surface endpoint: `/api/v1/summary`, `/api/v1/summary/trend-alignment`, and `/api/v1/signals`. It currently carries `daysSinceFlip` and `lastFlipTime` but no price data.

`SignalQueryService` already queries three signal-state result sets per call (latest, latest flip, earliest), and a separate `AssetLiquidityRepository` for volume. The candle table holds close prices keyed by `(asset_id, timeframe, close_time)`, and `CandleRepository` has `findByAssetAndTimeframeAndCloseTime` for point lookups and `findTopNByAssetIdAndTimeframe` for recency.

## Goals / Non-Goals

**Goals:**
- Add `pctChangeSinceFlip` to `SignalStateDto` — populated for every asset that has a recorded flip
- Compute in bulk to avoid N+1 queries — one query for flip-time closes, one for latest closes
- Enrich all endpoints that return `SignalStateDto` without any controller changes

**Non-Goals:**
- Storing price at flip time in `signal_state` (schema change not warranted for a derived field)
- Timeframe-specific percentage (W1 pct change vs D1 pct change) — always uses the same timeframe as the signal being returned
- Historical percentage at arbitrary dates

## Decisions

**Bulk candle fetch, not per-asset lookup**
`SignalQueryService` already has a list of `(asset, timeframe, closeTime)` tuples from the flip signal states. Rather than calling `findByAssetAndTimeframeAndCloseTime` N times, we fetch:
1. Latest candle close per asset — one native query returning `(assetId, closePrice)` pairs
2. Flip-time candle close per asset — batch lookup keyed on `(assetId, closeTime)`

Both can be done with two queries regardless of universe size. Results are put into maps and passed through the mapper.

*Alternative*: Join candle data in the existing signal-state queries — rejected because the signal-state queries use native SQL already tuned for their current shape; joining candles would complicate them and risk performance regressions.

**`null` when candle not found**
If a flip-time candle is missing (e.g. data gap or the flip pre-dates the backfill window), `pctChangeSinceFlip` is `null`. Callers already handle `null` on `daysSinceFlip`; same pattern applies.

**Rounded to 2 decimal places, signed**
Positive = price up since flip, negative = price down. Rounded to 2 d.p. in the mapper using `HALF_UP`. No absolute value — sign is meaningful (a bearish signal with a positive pct change means price has bounced against the trend).

**Markdown formatter: show alongside days-since-flip**
Format: `+12.34%` or `-3.21%` inline with the existing flip recency line. Both summary and trend-alignment formatters updated.

## Risks / Trade-offs

[Extra DB queries per `listSignals` call] Two additional queries added per `listSignals` invocation. Universe is currently ~45 assets; both queries are keyed on `asset_id` with existing indices. → Negligible at current scale; revisit if universe grows to thousands.

[Flip-time candle mismatch] The flip `closeTime` is the candle's `closeTime`. If the candle was deleted or never stored (backfill gap), `pctChangeSinceFlip` silently becomes `null`. → Acceptable; `null` is already the convention for missing flip data.
