## Why

Every asset in the `asset` table is currently an implicit crypto pair (the only `provider` value is `BINANCE`). There's no way to record that an asset is a crypto, stock, ETF, or commodity, which blocks both clearer reporting today and onboarding non-crypto assets later. We need a category on the asset itself, and the ability to filter the read APIs by it.

## What Changes

- Add an `asset_class` column to the `asset` table (`CRYPTO`, `STOCK`, `ETF`, `COMMODITY`), backed by a Postgres enum, `NOT NULL`, indexed for filtering. Backfill all existing rows to `CRYPTO` (the only class in use today).
- Add a corresponding `AssetClass` Java enum and thread it through `Asset`, `AssetDao`, and the seed migration (new assets must specify a class going forward).
- Add an optional `assetClass` query parameter to the read APIs that list or match individual assets — `GET /api/v1/signals`, `GET /api/v1/assets/by-state`, `POST /api/v1/scan`, `GET /api/v1/summary`, `GET /api/v1/trend-alignment`, and `GET /api/v1/candles/coverage` — filtering results to that class when supplied, with no filtering (all classes) when omitted.
- Include `assetClass` in the JSON response shape for per-asset results (`SignalSummary`, scan matches) so clients can see what class each result belongs to without a second lookup.

## Capabilities

### New Capabilities
- `asset-classification`: assets are categorized as crypto, stock, ETF, or commodity; the category is stored, queryable, and returned in API responses.

### Modified Capabilities
- (none — no existing spec files under `openspec/specs/` yet; the read-API filtering behavior is covered as part of `asset-classification` rather than as deltas to separately-specced capabilities)

## Impact

- **Schema**: new migration `V11__add_asset_class.sql` (enum type, column, index, backfill).
- **Code**: `Asset` record, `AssetDao` (new class-aware query method(s)), `SignalQueryService`, `ScanService`, and the `SignalsHandler`, `ScanHandler`, `SummaryHandler`, `TrendAlignmentHandler`, `CandleCoverageHandler` API handlers.
- **Response shape**: `SignalSummary` (and any DTO built from it, e.g. scan matches) gains an `assetClass` field — additive, not breaking.
- **Seed data**: `V8__seed_initial_assets.sql`'s existing rows all become `CRYPTO`; no data changes needed since it's a backfill default, but future seed/ingestion additions must set a class explicitly.
