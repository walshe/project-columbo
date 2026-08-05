## Why

`MarketBreadthPulseService` tallies bullish/bearish counts across every active asset regardless of class, and `market_breadth_snapshot` has no `asset_class` column at all. Since `add-asset-category-filter` shipped, `/summary?assetClass=STOCK` already filters its signal lists to stocks but still shows a `pulse` ratio computed across all 200 assets (crypto+stock+etf combined) — an inconsistency between two fields in the same response.

## What Changes

- Add an `asset_class` column to `market_breadth_snapshot`. Nullable: `NULL` means "combined across every class" (today's existing behavior, made explicit rather than accidental), a specific class means "this class only" — same nullable-optional-filter convention already used for `assetClass` everywhere else in this API (`SignalQueryService`, `AssetDao`, etc.).
- `MarketBreadthPulseService.computeForAllActiveAssets` gains an `AssetClass` filter parameter, threading through to `AssetDao.findAllActive(assetClassFilter)` (already exists from the prior change) so counts are scoped correctly.
- The daily pipeline (`PipelineOrchestrator`) computes and persists one snapshot per `(timeframe, assetClass)` combination, plus one combined (`assetClass = null`) snapshot per timeframe — mirroring the "one combined + one per class" shape the read APIs already expose. A class with zero active assets simply produces no snapshot (existing "no signal state yet, skipping" behavior), not an error.
- `SummaryHandler` passes its already-existing `assetClass` query param through to `marketBreadthSnapshotDao.findLatest(timeframe, assetClass)` instead of the unfiltered `findLatest(timeframe)` — this is the actual bug fix: `/summary`'s `pulse` field will now match its `assetClass` filter instead of silently ignoring it.
- **Not building**: an endpoint or response shape that returns multiple per-class breakdowns in a single request. Omitting `assetClass` continues to mean "combined across everything," identical to every other filterable field in this API — not "give me a breakdown per class." (Considered and rejected — see `design.md`.)

## Capabilities

### New Capabilities
- (none — this modifies existing behavior, no new standalone capability)

### Modified Capabilities
- `market-breadth-pulse`: snapshots are now computed and stored per asset class (plus one combined snapshot), instead of always combining every active asset regardless of class.
- `summary-api`: the `pulse` field in `/summary`'s response now respects the request's `assetClass` filter instead of always reflecting every asset combined.

## Impact

- **Schema**: new migration adding `asset_class` (nullable) to `market_breadth_snapshot`, replacing its `UNIQUE (timeframe, snapshot_close_time)` constraint with one that also accounts for `asset_class` (including the `NULL` "combined" case as its own idempotent identity — see `design.md` for the constraint mechanics).
- **Code**: `MarketBreadthSnapshot`, `MarketBreadthSnapshotDao`, `MarketBreadthPulseService`, `PipelineOrchestrator`, `SummaryHandler`.
- **Response shape**: `MarketBreadthSnapshot` (embedded in `/summary`'s JSON) gains an `assetClass` field — additive, not breaking. `pulse`'s actual counts change (now class-scoped) when `assetClass` is supplied; unfiltered requests are unaffected in shape, and in value as long as every currently-active asset was crypto at the time this ships (true today).
- **Pipeline cost**: one `computeForAllActiveAssets` call per `(timeframe, assetClass)` pair instead of one per timeframe — bounded (currently 2 timeframes x 5 possibilities [combined + 4 classes] = 10 calls/run instead of 2), and each call is a fast in-memory tally over already-fetched signal state, not a new expensive query shape.
