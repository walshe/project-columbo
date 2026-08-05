## Context

`asset` currently has no notion of instrument type — every row is an implicit crypto pair since `provider` only has a `BINANCE` value. `AssetDao.findAllActive()` is the single chokepoint that all read APIs pull assets through (directly, or transitively via `SignalQueryService`), so adding a class filter there is the one place that fans out correctly to `/signals`, `/assets/by-state`, `/scan`, `/summary`, `/trend-alignment`, and `/candles/coverage`. This is why the change touches five handlers and three service-layer classes even though the underlying data model change is a single column.

## Goals / Non-Goals

**Goals:**
- Persist a category on every asset: `CRYPTO`, `STOCK`, `ETF`, `COMMODITY`.
- Let every read API that returns or matches individual assets optionally filter by that category.
- Keep the change additive/non-breaking: omitting the new query param returns exactly what callers get today.

**Non-Goals:**
- No new ingestion/provider support for stocks, ETFs, or commodities in this change — `provider` stays `BINANCE`-only. This change only adds the categorization field and filtering; onboarding a non-crypto data source is separate future work.
- No multi-class-per-asset support (e.g. an asset tagged as both ETF and commodity) — one class per asset, `NOT NULL`.
- No changes to ingestion, indicator computation, or market-breadth logic — those already operate per-asset regardless of class and don't need to know about it.

## Decisions

- **Postgres enum (`asset_class`) over a free-text column or a lookup table.** The codebase already uses this pattern for `timeframe` and `provider` (`V2__create_asset_and_candle.sql`) — a fixed, small, rarely-changing value set. Consistent with existing conventions and gives cheap validation at the DB layer for free, matching `Timeframe`/`Provider`'s existing Java enum + Postgres enum pairing.
- **`NOT NULL` with a backfill default, not nullable.** A nullable class would mean every filter and every response field has to handle "unknown," permanently. Since every existing row is unambiguously `CRYPTO` today, backfilling once in the migration is simpler and keeps the column non-nullable going forward (`Asset`'s compact constructor can `Objects.requireNonNull` it like every other field, per this codebase's constructor-validation convention).
- **Filter parameter threaded through `AssetDao`, not applied as a post-fetch `Stream.filter` in the service layer.** `findAllActive()` already does the equivalent of a `WHERE active = true` filter in SQL; adding `assetClass` to the same query (as an optional `AND asset_class = ?`) is one extra DAO method (`findAllActive(AssetClass filter)` or an overload) rather than fetching everything and filtering in Java, which stays consistent with how `active` is already handled and avoids paying the full-table cost when a caller only wants one class.
- **`assetClass` added to `SignalSummary` (and scan match DTOs) rather than left as a request-only filter.** Once results can be filtered by class, callers reasonably expect to see which class each result belongs to without a second `/signals?assetClass=X` round trip per class. Additive JSON field, no existing consumer breaks.
- **Index on `asset_class` alone, not composite with `active`.** `active` is already low-cardinality and most queries also filter it in the `WHERE` clause directly; a single-column btree on `asset_class` is enough for the filter to hit an index rather than a seq scan on the (currently ~60-row, but growing) table, matching the precedent set by `V10__index_candle_timeframe_close.sql` (added for a real query pattern, not preemptively).

## Risks / Trade-offs

- [Every new asset onboarded from now on must specify a class, or the insert fails `NOT NULL`] → Acceptable and intentional: forces explicit categorization at asset-creation time rather than letting it silently default, consistent with this change's goal.
- [Five handlers touched increases the surface area for this change to introduce a regression in existing (non-filtered) behavior] → Each handler's existing query-param handling pattern (`ctx.queryParamAsClass(...).allowNullable().get()`, same idiom `state` already uses on `SignalsHandler`) is copied as-is; when `assetClass` is absent the DAO call is the same "no filter" path as today, so existing tests without the new param should require no changes beyond expected-response-shape (`assetClass` field) tweaks.
- [Seed data backfill choice (`CRYPTO` for all existing rows) is correct today but is an assumption baked into the migration] → Verified: `V8__seed_initial_assets.sql` currently only inserts `BINANCE`-provider crypto pairs, so this is a safe backfill, not a guess.

## Migration Plan

1. `V11__add_asset_class.sql`: `CREATE TYPE asset_class AS ENUM ('CRYPTO', 'STOCK', 'ETF', 'COMMODITY');` then add the column `NOT NULL DEFAULT 'CRYPTO'` (default makes the backfill implicit and safe for the existing rows in one statement), then an index `idx_asset_asset_class`. Drop the `DEFAULT` after backfill is unnecessary — keeping a default of `CRYPTO` going forward is fine since it matches this codebase's actual current asset mix and any future non-crypto insert already has to specify columns explicitly.
2. Update `Asset` record, `AssetDao`, `SignalQueryService`, `ScanService`, and the five handlers in the same change (no separate rollout phases needed — Flyway migration and code ship together as one deployable unit, same as every other migration in this project).
3. Rollback: standard Flyway convention in this project is forward-only migrations (no down-migrations exist for V1–V10) — a bad deploy is fixed by a new forward migration, not a rollback script.

## Open Questions

- None — the seed data's class assignment, the query-param naming (`assetClass`, matching the `timeframe`/`state` casing convention already used), and the filter's DAO placement are all resolved by precedent already in the codebase.
