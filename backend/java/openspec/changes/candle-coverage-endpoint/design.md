## Context

Data-freshness is currently exposed only as `lastIngestionAt` / `candlesThrough` fields piggybacked on signal/summary responses; `IngestionStatusService` computes them from `CandleRepository.findLatestCloseTimeForTimeframe` and the ingestion-run table. There is no endpoint to see, per timeframe, how far back coverage goes, how current it is, and how many assets are covered. `CandleRepository` already has `findLatestCloseTimeForTimeframe` (MAX close_time) and handles PostgreSQL native-query return-type quirks via a `toOffsetDateTime` helper. The ingestion pipeline already computes a "finalized boundary" (candles before today's UTC midnight) via `CandleFilters.utcMidnightToday`.

## Goals / Non-Goals

**Goals:**
- One read endpoint that answers "is candle history deep enough and current?" per timeframe
- Reuse existing repository/finalized-boundary logic rather than introduce parallel notions of "latest"
- Cheap: a small number of aggregate queries, no per-candle scans

**Non-Goals:**
- Per-asset breakdown (summary only; a per-asset view can be a later change)
- Alerting/notification on staleness (reporting only)
- Any backfill/repair action triggered from the endpoint

## Decisions

**New `CandleCoverageService` + thin controller, mirroring existing structure**
`CandleCoverageController` (`GET /api/v1/candles/coverage`) delegates to `CandleCoverageService`, which builds one entry per `Timeframe` value. This matches the existing controller/service split (e.g. `SignalController` → `SignalQueryService`) and keeps the timeframe iteration in one place. The response is a map/list keyed by timeframe.

**Aggregate queries on `CandleRepository`, not in-memory**
Add per-timeframe `MIN(close_time)`, `MAX(close_time)` (MAX already exists), and `COUNT(DISTINCT asset_id)` queries. Computing these in SQL keeps the endpoint O(index) rather than loading candles. Native queries follow the existing `CAST(:timeframe AS timeframe)` pattern and reuse the `toOffsetDateTime` conversion for mixed return types.

**`expectedLatest` reuses the pipeline's finalized-boundary definition**
For each timeframe, `expectedLatest` is the most recent finalized period close as of `now` (via the existing `CandleFilters` boundary logic / `TimeProvider`), so "up to date" means the same thing here as it does to the ingestion pipeline. `upToDate = latest != null && latest >= expectedLatest`. Deriving it from the same source avoids a second, divergent definition of "current".

**Empty timeframe returns a populated entry, not an omission**
A timeframe with no candles yields `earliest=null`, `latest=null`, `assetCount=0`, `upToDate=false`. Callers get a stable shape (every timeframe present) and can distinguish "empty" from "missing".

## Risks / Trade-offs

[`expectedLatest` for W1 must match how weekly candles are finalized] The weekly finalized boundary differs from daily. → Derive both from the same timeframe-aware boundary helper the pipeline uses so the endpoint can't disagree with ingestion about what "finalized" means.

[Timezone/return-type quirks of native aggregate queries] PostgreSQL may return `Timestamp`/`Instant`/`OffsetDateTime`. → Reuse the existing `toOffsetDateTime` normalisation already proven in `IngestionStatusService`.
