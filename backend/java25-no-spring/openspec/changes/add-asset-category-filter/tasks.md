## 1. Schema

- [ ] 1.1 Add `V11__add_asset_class.sql`: `CREATE TYPE asset_class AS ENUM ('CRYPTO', 'STOCK', 'ETF', 'COMMODITY')`, add `asset_class` column to `asset` (`NOT NULL DEFAULT 'CRYPTO'`), add index `idx_asset_asset_class`.

## 2. Domain model

- [ ] 2.1 Add `AssetClass` enum (`walshe.projectcolumbo.supertrend.shared`, alongside `Timeframe`/`Provider`).
- [ ] 2.2 Add `assetClass` field to the `Asset` record, with `Objects.requireNonNull` in the compact constructor per this codebase's constructor-validation convention.
- [ ] 2.3 Add a `CandleTest`/`AssetTest`-style unit test covering the new null-check.

## 3. Persistence

- [ ] 3.1 Update `AssetDao.findAllActive()`'s row mapper to read `asset_class`.
- [ ] 3.2 Add `AssetDao.findAllActive(AssetClass filter)` (or equivalent overload) that adds `AND asset_class = ?` to the existing query, returning the same results as `findAllActive()` when `filter` is null.
- [ ] 3.3 Update `V8__seed_initial_assets.sql`'s comment/insert if needed to reflect the new `NOT NULL DEFAULT 'CRYPTO'` column (no data change expected — confirm the default covers it).
- [ ] 3.4 Add an `AssetDaoIntegrationTest` (none currently exists) covering `findAllActive(AssetClass)` filtering, including the zero-match case.

## 4. Service layer

- [ ] 4.1 Update `SignalQueryService.listSignals` to accept an optional `AssetClass` filter and pass it through to `AssetDao`.
- [ ] 4.2 Add `assetClass` to `SignalSummary`, populated from the `Asset` each signal is built from.
- [ ] 4.3 Update `ScanService` to accept and apply the same optional filter, and include `assetClass` on scan match results.
- [ ] 4.4 Update existing `SignalQueryServiceIntegrationTest`/`ScanServiceTest` call sites for the new parameter and response field.

## 5. HTTP API

- [ ] 5.1 `SignalsHandler`: add `assetClass` query param to `GET /api/v1/signals` and `GET /api/v1/assets/by-state` (same `ctx.queryParamAsClass(...).allowNullable().get()` idiom as `state`), update `@OpenApi` annotations.
- [ ] 5.2 `ScanHandler`/`ScanRequest`: add optional `assetClass` field to the scan request body, validated the same way as other optional fields, update `@OpenApi` annotations.
- [ ] 5.3 `SummaryHandler`: add `assetClass` query param, thread through to `SignalQueryService`/formatter.
- [ ] 5.4 `TrendAlignmentHandler`: add `assetClass` query param, thread through.
- [ ] 5.5 `CandleCoverageHandler`: add `assetClass` query param, thread through to whatever asset lookup backs coverage.
- [ ] 5.6 Update each handler's `*IntegrationTest` with cases for: filter present (matches), filter present (no matches → empty, not error), filter absent (unchanged existing behavior).

## 6. Formatters

- [ ] 6.1 Check `SignalTextFormatting`/`TrendAlignmentFormatter`/`SummaryFormatter` (Markdown/Watchlist output) for whether `assetClass` should also render there; update if the existing filter-metadata convention (timeframe/state already shown per the earlier report-filter-metadata change) implies it should.

## 7. Docs

- [ ] 7.1 Update `README.md`'s endpoint/query-param documentation for the six changed endpoints.
- [ ] 7.2 Note the new `AssetClass` enum and column in `developer-notes.md` if it documents the schema/domain model elsewhere.

## 8. Verification

- [ ] 8.1 Run full `mvn test` suite, confirm no regressions.
- [ ] 8.2 Run `mvn verify -Pe2e` to confirm the end-to-end pipeline test still passes with the new column/default in place.
- [ ] 8.3 Self-review via the `java-code-review` skill before opening the PR.
