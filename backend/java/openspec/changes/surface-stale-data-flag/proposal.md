## Why

Read endpoints (`/signals`, `/summary`, `/summary/trend-alignment`) currently return `lastIngestionAt` and `candlesThrough`, but a consumer has to know the finalized-candle schedule to decide whether that data is actually current. When the daily pipeline fails or lags, these endpoints silently serve stale signals — exactly the situation that made SuperTrend "not line up" recently. We now have the coverage logic to detect this; we should surface it as a first-class flag rather than making every caller recompute it, while keeping the API available (a stale-but-flagged response beats a blanket outage).

## What Changes

- Add a `CandleFreshnessService` as the single source of truth for "is timeframe X up to date as of now?", reusing the finalized-boundary definition from the coverage feature (`utcMidnightToday`). Refactor `CandleCoverageService` to use it so coverage and the new flag can never disagree.
- Add a `stale` boolean to the freshness-bearing response wrappers (`SignalListResponse`, `SummaryReport`, `ConfluenceSummaryReport`). It reflects whether the queried timeframe has the most recent finalized candle (for the cross-timeframe trend-alignment report, D1 — the driving timeframe).
- Add an optional `requireFresh` query parameter (default `false`) on those endpoints. When `true` and the data is stale beyond a short grace period covering the normal post-boundary ingestion window, the endpoint returns `503 Service Unavailable` with an explanatory body instead of stale data. Default behaviour is unchanged (serve with `stale` flag).
- **Not** changing the default to hard-fail — staleness is surfaced, and enforcement is opt-in per request.

## Capabilities

### New Capabilities

- `data-freshness`: A shared determination of per-timeframe data currency, surfaced as a `stale` flag on read responses and optionally enforced per request via `requireFresh`.

### Modified Capabilities

_(none — the coverage endpoint's behaviour is unchanged; it is refactored to share the freshness logic but its output is identical)_

## Impact

- New `CandleFreshnessService` (per-timeframe `isUpToDate` / `expectedLatest`), reused by `CandleCoverageService`
- `SignalListResponse`, `SummaryReport`, `ConfluenceSummaryReport` — new `stale` field (additive)
- `SignalController`, `SummaryController`, `ConfluenceSummaryController` (or their services) — populate `stale`; handle `requireFresh` → 503
- **Out of scope**: hard-failing by default, per-asset freshness, alerting/monitoring wiring, and any change to ingestion itself
