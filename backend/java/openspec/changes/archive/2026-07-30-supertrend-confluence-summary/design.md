## Context

The existing `SummaryService` runs a single-timeframe scan. `SignalQueryService.listSignals()` returns all assets in a given trend state for a given timeframe. There is currently no service that joins W1 and D1 signal states to find assets aligned on both. The Elder summary already does W1+D1 alignment but for the Elder Impulse indicator — this change brings the same concept to the simpler SuperTrend signals that are always computed (Elder is disabled).

## Goals / Non-Goals

**Goals:**
- New endpoint returns bull-aligned (W1 bullish + D1 bullish) and bear-aligned (W1 bearish + D1 bearish) asset lists
- Ordered by when the D1 signal fired (most recent first) — this is when confluence became tradeable
- Supports JSON and MARKDOWN response formats
- Reuses existing infrastructure — no new DB queries, no schema changes

**Non-Goals:**
- Cross-timeframe alignment for RSI or any indicator other than SuperTrend
- More than two timeframes (e.g. monthly + weekly + daily triple alignment)
- Replacing or modifying the existing `/api/v1/summary` endpoint

## Decisions

**1. Join in application layer, not SQL**

Fetch W1 bullish signals and D1 bullish signals separately via `SignalQueryService`, then intersect by asset symbol in Java. This reuses the existing query path and avoids a bespoke cross-timeframe SQL join.

Alternative considered: a single native query joining signal_state rows across timeframes. Rejected — adds SQL complexity for marginal performance gain given the universe size (~100 assets).

**2. Order by D1 flip date**

The D1 signal firing is the moment confluence becomes actionable — the W1 signal may have been in place for weeks. Ordering by D1 flip date (most recent first) surfaces the freshest setups at the top.

**3. New dedicated endpoint rather than a scan preset**

`/api/v1/summary/confluence` is a clean consumer contract — returns both bull and bear lists in one call, owns its ordering, and can evolve independently. A scan preset would require two separate calls and would expose the intersection logic to the caller.

**4. New DTO and controller, minimal new service logic**

`ConfluenceSummaryService` does the intersection and ordering. `ConfluenceSummaryReport` is a simple record. The formatter's existing `formatMarkdown` infrastructure (data freshness header, signal list rendering) is extended rather than duplicated.

## Risks / Trade-offs

- **Stale signal states**: If the pipeline hasn't run today, results reflect yesterday's alignment. Mitigated by including `lastIngestionAt` and `candlesThrough` in the response, same as the existing summary.
- **Missing D1 flip date for ordering**: If `daysSinceFlip` is null on a signal state (asset has always been in the same state), it sorts to the end. This is acceptable — those are long-established trends, not fresh confluences.
