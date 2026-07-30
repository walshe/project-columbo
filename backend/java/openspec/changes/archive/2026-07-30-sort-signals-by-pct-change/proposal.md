## Why

The `/api/v1/signals` endpoint can order results by flip recency, asset, trend state, and liquidity — but not by how far price has moved since the flip. Now that every `SignalStateDto` carries `pctChangeSinceFlip`, sorting by it lets a caller surface the assets with the strongest confirming move (a bullish name up 20% since flipping, or a bearish name down 15%), which is a distinct screening question from "what flipped most recently".

## What Changes

- Add two sort options, `PCT_CHANGE_ASC` and `PCT_CHANGE_DESC`, to the `SignalSort` enum
- Wire them into `SignalQueryService.listSignals` so `/api/v1/signals?sort=PCT_CHANGE_DESC` (and `_ASC`) orders results by `pctChangeSinceFlip`
- **Trend-relative by design**: the value is signed, so the caller picks the direction that means "trend confirming most strongly" — `DESC` for a bullish list (biggest gain on top), `ASC` for a bearish list (biggest drop on top). The service does not infer direction from the state filter.
- Assets with no recorded flip (`null` `pctChangeSinceFlip`) always sort last, regardless of direction
- Scope is the `/api/v1/signals` endpoint only. The `/api/v1/summary` and `/api/v1/summary/trend-alignment` reports keep their existing recency ordering and are untouched.

## Capabilities

### New Capabilities

- `signal-sort`: Ordering options for the signal list endpoint, including the new percentage-change-since-flip sort

### Modified Capabilities

_(none — `SignalSort` is an internal enum and the sort behaviour has no existing versioned spec)_

## Impact

- `SignalSort` — two new enum constants
- `SignalQueryService.listSignals` — two new `case` branches in the existing sort `switch`
- `/api/v1/signals` — accepts the new `sort` values automatically (Spring binds the query param straight to the enum; no controller change)
- **No changes** to `SummaryService`, `ConfluenceSummaryService`, mappers, repositories, or the DTO
- Existing `SignalQueryServiceTest` gains coverage for the new sort ordering (including nulls-last)
