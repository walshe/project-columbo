## 1. Schema

- [x] 1.1 Add `V13__add_asset_class_to_market_breadth_snapshot.sql`: add nullable `asset_class asset_class` column to `market_breadth_snapshot`; drop the old `unique_market_breadth_snapshot` constraint; add a `COALESCE(asset_class::text, '__ALL__')`-based unique index in its place; replace `idx_market_breadth_lookup` with one leading on `(timeframe, asset_class, snapshot_close_time DESC)`.

## 2. Domain model

- [x] 2.1 Add `assetClass` field to `MarketBreadthSnapshot` record (nullable — `Objects.requireNonNull` only where non-null is actually required elsewhere; this field itself is legitimately nullable, matching the "null = combined" convention).
- [x] 2.2 Add/update a `MarketBreadthSnapshotTest` case covering the record still validates its existing invariants (non-negative counts, ratio bounds) with the new field present.

## 3. Persistence

- [x] 3.1 `MarketBreadthSnapshotDao.upsert`: include `asset_class` in the INSERT and the `ON CONFLICT` target (matching the new `COALESCE`-based unique index expression exactly).
- [x] 3.2 `MarketBreadthSnapshotDao.findLatest(Timeframe, AssetClass)`: filter by class using `IS NOT DISTINCT FROM` (not `=`) so the `NULL`/combined case matches correctly. Keep a no-arg-filter overload if any caller still wants "combined" without spelling out `null`.
- [x] 3.3 `MarketBreadthSnapshotDao.findRange(Timeframe, AssetClass, OffsetDateTime, OffsetDateTime)`: same `IS NOT DISTINCT FROM` filtering, for consistency (currently unused by any handler, but would silently return every class's rows mixed together once this column exists if left unfiltered).
- [x] 3.4 Update `PersistenceIntegrationTest`'s `marketBreadthSnapshotDaoUpsertAndQueries` call sites for the new parameter.

## 4. Service layer

- [x] 4.1 `MarketBreadthPulseService.computeForAllActiveAssets(Timeframe, AssetClass)`: thread the filter through to `AssetDao.findAllActive(assetClassFilter)`, keep the existing "no signal state yet, skip" behavior unchanged for a class with zero active assets.
- [x] 4.2 Update existing `MarketBreadthPulseServiceIntegrationTest` call sites for the new parameter; add a test asserting a per-class snapshot's counts only reflect that class's assets, and that computing for a class with zero active assets produces no snapshot.

## 5. Pipeline

- [x] 5.1 `PipelineOrchestrator.executePhases`: replace each single `marketBreadthPulseService.computeForAllActiveAssets(timeframe)` call with one combined call (`null` filter) plus one call per `AssetClass` value, for both D1 and W1.
- [x] 5.2 Update `PipelineOrchestrator` tests/E2E expectations if any assert on the exact sequence or count of pulse-service invocations.

## 6. HTTP API

- [x] 6.1 `SummaryHandler.buildResponse`: pass the already-parsed `assetClass` through to `marketBreadthSnapshotDao.findLatest(timeframe, assetClass)` instead of the unfiltered call.
- [x] 6.2 Update `SummaryHandlerIntegrationTest` with a case proving `pulse` changes (not just the signal lists) when `assetClass` is supplied, and that omitting it still combines every class as before.

## 7. Docs

- [x] 7.1 Update `README.md`'s `/summary` row/description if it currently implies `pulse` is unfiltered.
- [x] 7.2 Note the new snapshot key shape (`timeframe, snapshot_close_time, asset_class`) in `developer-notes.md` if it documents the schema elsewhere.

## 8. Verification

- [x] 8.1 Run full `mvn test` suite, confirm no regressions.
- [x] 8.2 Run `mvn verify -Pe2e` to confirm the end-to-end pipeline test still passes with the new snapshot shape.
- [x] 8.3 Self-review via the `java-code-review` skill before opening the PR.
