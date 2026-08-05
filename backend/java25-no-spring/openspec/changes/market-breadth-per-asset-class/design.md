## Context

`market_breadth_snapshot` is keyed on `(timeframe, snapshot_close_time)` only, uniquely per run — `MarketBreadthPulseService.computeForAllActiveAssets(timeframe)` tallies every active asset into one snapshot regardless of class. This predates asset classification entirely (the table was created before `add-asset-category-filter`), so it was never wrong on its own terms — it's only inconsistent now that `/summary` accepts an `assetClass` filter for its signal lists but silently ignores it for `pulse`.

## Goals / Non-Goals

**Goals:**
- `pulse` in `/summary`'s response reflects the same `assetClass` filter already applied to that request's signal lists.
- Preserve today's "combined across everything" behavior as the explicit, honest default when `assetClass` is omitted — not a removed capability.
- Keep the per-run pipeline cost bounded and proportional to the (small, fixed) number of asset classes, not to asset count.

**Non-Goals:**
- No endpoint or response shape that returns every class's breakdown in one request (see Decisions — considered and rejected).
- No change to how bullish/bearish/missing counts are computed per asset (`SignalQueryService`/`SignalStateDetectionService` untouched) — only which assets get counted into a given snapshot.
- No backfill/recompute of historical snapshot rows for a class breakdown that didn't exist when they were captured — see Migration Plan.

## Decisions

- **Nullable `asset_class` column, `NULL` = combined, not a separate "ALL" enum value.** Keeps `AssetClass` (`CRYPTO`/`STOCK`/`ETF`/`COMMODITY`) purely a per-asset categorization — every existing use of it (`Asset.assetClass()`, `SignalSummary.assetClass()`) means "what this asset actually is," and an "ALL" pseudo-value there would be meaningless (no asset is ever "class ALL"). `NULL` on the snapshot column instead means "which filter produced this row," matching how `assetClass` already means "no filter" everywhere else it's nullable in this API (`SignalQueryService.listSignals`, `AssetDao.findAllActive`, etc.) — one consistent convention, not two.
- **Two partial unique indexes (one for `asset_class IS NULL`, one for `IS NOT NULL`) instead of a plain `UNIQUE (timeframe, snapshot_close_time, asset_class)` constraint.** Standard SQL treats every `NULL` as distinct from every other `NULL`, so a plain three-column unique constraint would let multiple "combined" rows accumulate for the same `(timeframe, snapshot_close_time)` instead of upserting into one — silently breaking `MarketBreadthSnapshotDao.upsert`'s idempotency for exactly the `NULL` (combined) case, the one that runs on every single pipeline execution.
  - First attempt: a `COALESCE(asset_class::text, '__ALL__')` expression index, collapsing `NULL` to a fixed sentinel for uniqueness purposes only. Postgres rejected it at migration time (`functions in index expression must be marked IMMUTABLE`) — an enum's `::text` cast goes through the type's output function, which Postgres classifies `STABLE` (new labels can be added to an enum later, so its output isn't guaranteed stable across all possible catalog states), not `IMMUTABLE`, and index expressions must be `IMMUTABLE`.
  - Working approach: `unique_market_breadth_snapshot_combined` (`UNIQUE (timeframe, snapshot_close_time) WHERE asset_class IS NULL`) and `unique_market_breadth_snapshot_per_class` (`UNIQUE (timeframe, snapshot_close_time, asset_class) WHERE asset_class IS NOT NULL`) — two plain partial indexes, no function calls, so immutability never comes into question. The tradeoff: a single `INSERT` can't pick its `ON CONFLICT` target based on a bound parameter's runtime nullability, so `MarketBreadthSnapshotDao.upsert` runs one of two fixed SQL statements depending on whether `snapshot.assetClass()` is `null`, rather than one parameterized statement.
- **Pipeline computes one snapshot per class plus one combined snapshot, not a breakdown-on-read.** Computing at write time (once per pipeline run, from already-fetched signal state) is cheap and keeps `/summary` a simple point read (`findLatest`), consistent with how the rest of this endpoint already works — no new expensive aggregation happens on the request path.
- **Omitting `assetClass` continues to mean "combined," not "give me every class's breakdown."** Considered making the unfiltered case return a per-class breakdown map instead, since it's arguably more informative. Rejected: it would make `SummaryResponse.pulse`'s type conditional (sometimes one snapshot, sometimes N), breaking the fixed response shape and its OpenAPI schema, and it's inconsistent with every other filterable field in this API, where omission already means "combined," not "enumerate every value." A future dedicated breakdown view (e.g. a `/market-breadth` endpoint listing every class's pulse side by side) is a cleaner way to serve that need later, if wanted — additive, not a change to `/summary`'s existing contract.
- **A class with zero active assets produces no snapshot for that run, not an empty/zero one.** Matches `computeForAllActiveAssets`'s existing behavior when there's no signal state yet (`LOG.info(...); return;`) — a class simply not queried through `findLatest(timeframe, thatClass)` returns `Optional.empty()`, same as "no snapshot exists yet" already means today for a timeframe with no data. No special-casing needed.

## Risks / Trade-offs

- [Pipeline now makes up to 5x more `computeForAllActiveAssets` calls per timeframe (combined + 4 classes, was 1)] → Each call is a cheap in-memory tally over signal state already fetched earlier in the same phase (`SignalStateDao.findLatestForAllAssets`, not re-queried per class) — bounded by the fixed number of asset classes, not by asset count, so this scales with future class additions (rare) rather than future asset additions (frequent, e.g. the 140-asset stock/ETF batch).
- [Existing historical `market_breadth_snapshot` rows predate this column] → They get `asset_class = NULL` automatically (nullable column, no default needed) — correct and desired: those rows genuinely were "combined across whatever was active then" (all crypto, in practice, until this change ships), so no backfill/migration statement is needed for them to remain accurate under the new "NULL = combined" meaning.
- [`COALESCE`-based unique index is a less-common pattern than earlier migrations in this project used] → Documented here and in the migration file's own comment; it's the standard, well-supported Postgres idiom for "treat NULL as a normal value for uniqueness purposes" (no extension, no trigger).

## Migration Plan

1. New migration: add nullable `asset_class` to `market_breadth_snapshot`, drop the old `UNIQUE (timeframe, snapshot_close_time)` constraint, add the `COALESCE`-based unique index in its place, and replace `idx_market_breadth_lookup` with one that leads on `(timeframe, asset_class, snapshot_close_time DESC)` so `findLatest(timeframe, assetClass)` stays index-backed.
2. Ship `MarketBreadthSnapshotDao`/`MarketBreadthPulseService`/`PipelineOrchestrator`/`SummaryHandler` changes in the same change (no phased rollout — same pattern as every other migration in this project, schema and code ship together).
3. Rollback: forward-only, per this project's existing Flyway convention (no down-migrations exist for V1–V12) — a bad deploy is fixed by a new forward migration.

## Open Questions

- None — the nullable-column-plus-`COALESCE`-index approach resolves the only real design ambiguity (how "combined" and "per-class" coexist under one idempotent upsert key).
