## Context

`CandleCoverageService` already computes, per timeframe, `expectedLatest = utcMidnightToday(now) - periodDays` and `upToDate = latest != null && latest >= expectedLatest`, using `TimeProvider` and `CandleRepository`. Read responses (`SignalListResponse`, `SummaryReport`, `ConfluenceSummaryReport`) already carry `lastIngestionAt` / `candlesThrough`, assembled in the controllers/services from `IngestionStatusService`. There is no single reusable "is X current?" component — the coverage logic lives inside the coverage service. Because crypto trades 24/7 and the pipeline runs at 00:05 UTC, D1 `upToDate=false` almost always means the day's run failed rather than normal lag — except for a brief window right after the period boundary before the run completes.

## Goals / Non-Goals

**Goals:**
- One shared definition of per-timeframe currency, reused by coverage and the new flag
- Surface staleness on read responses without forcing callers to recompute it
- Let a caller opt into strict behaviour when it genuinely cannot use stale data
- Preserve availability by default (flag, not fail)

**Non-Goals:**
- Hard-failing reads by default (rejected — couples read availability to pipeline health with a large blast radius)
- Per-asset freshness (timeframe-level only)
- Alerting/monitoring wiring (a healthcheck over the coverage endpoint is a separate concern)
- Any change to ingestion scheduling or backfill

## Decisions

**Extract `CandleFreshnessService` and have coverage depend on it**
Move the `expectedLatest` / `upToDate` computation into a `CandleFreshnessService` exposing `expectedLatest(Timeframe)` and `isUpToDate(Timeframe)`. `CandleCoverageService` calls it so its `expectedLatest`/`upToDate` output is unchanged but no longer the definition's owner. The read paths call `isUpToDate(timeframe)` and set `stale = !upToDate`. Single definition → coverage and flag cannot drift.

**`stale` reflects the timeframe the response is about**
`/signals?timeframe=X` and `/summary?timeframe=X` use X. The trend-alignment report spans W1+D1; it uses **D1**, since W1 is rolled up from D1 (if D1 is behind, W1 is too) and D1 is the fast-moving driver. Keeps a single boolean meaningful without enumerating per-timeframe flags.

**`requireFresh` returns 503, not 409, with a grace period**
503 Service Unavailable fits "the server can't currently satisfy the freshness contract"; a `Retry-After` hint is natural. Enforcement applies a grace period measured from the period boundary so the daily ~post-midnight ingestion window (data legitimately not yet ingested) does not 503 every day. The `stale` flag itself stays literal (true the moment the latest finalized candle is absent); only `requireFresh` enforcement honours the grace. Default `requireFresh=false` preserves current behaviour exactly.

**Additive response field**
`stale` is added to the existing wrapper records. Additive and backward-compatible; typed clients gain a field, untyped clients ignore it.

## Risks / Trade-offs

[`requireFresh` 503s during the normal post-boundary window] → The grace period (covering the run cadence, e.g. a small number of hours) suppresses false positives; the honest `stale` flag remains available for callers that want the literal state.

[One boolean can't express partial freshness on the cross-timeframe report] → Deliberately scoped to D1; callers needing W1 detail can use `/candles/coverage`. Avoids a combinatorial freshness object on every response.

[Grace-period value is a judgement call] → Make it a named constant/config with a comment; default sized to comfortably cover a delayed daily run without masking a genuinely dead pipeline.
