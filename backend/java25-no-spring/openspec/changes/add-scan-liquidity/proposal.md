## Why

`/api/v1/scan` is the condition-based shortlist endpoint - the closest thing this system has to
"assets worth a manual look." It currently returns zero liquidity signal per match, so a scan
match on a thin/illiquid symbol looks identical to one on a highly liquid one. `/api/v1/signals`
already solved this (`avgVolume7d` field, `sort=LIQUIDITY_DESC`, backed by the existing
`v_asset_liquidity` view / `AssetLiquidityDao`) - `/scan` just never got the same treatment.

## What Changes

- Add `avgVolume7d` to `ScanResult` (once per matched asset, not duplicated per matched
  condition - `ScanResult` already groups a symbol's `ScanConditionMatch` list, so this is the
  natural home for a single per-asset liquidity figure).
- Add an optional sort to `POST /api/v1/scan`'s request body, defaulting to today's implicit
  symbol-ascending order, with a `LIQUIDITY_DESC` option - mirroring `SignalSort`'s existing
  descending-only pattern (and its documented rationale: liquidity sort exists to surface the
  most-tradeable matches first, so an ascending variant has no real use case).
- No changes to `/signals`, `/assets/by-state`, or `/summary` - this closes the gap on `/scan`
  specifically.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `scan-api`: `ScanResult` gains `avgVolume7d`; `ScanRequest` gains an optional `sort` field.

## Impact

- `ScanResult` (new field) - a response shape change, additive/non-breaking (existing clients
  ignoring unknown JSON fields are unaffected; nothing is removed or renamed).
- `ScanRequest` (new optional field) - additive, existing request bodies remain valid (sort
  defaults to current behavior when omitted).
- `ScanService.combine`/`ScanService.execute` - needs the asset's `avgVolume7d` (currently only
  read from `SignalSummary` inside per-condition matching, not threaded through to `ScanResult`)
  and a sort branch alongside the existing symbol-ascending sort.
- `ScanHandler` - `@OpenApi` request/response docs need updating for the new fields.
- Tests: `ScanServiceTest` (pure `combine` logic), `ScanServiceIntegrationTest`,
  `ScanHandlerIntegrationTest`.
