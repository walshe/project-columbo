## Why

`GET /api/v1/signals` can filter by trend state and asset class, but not by a specific symbol or set of symbols - the only way to check one asset's current trend is to fetch the entire active-asset list for a timeframe and filter client-side. This is a real cost now that the endpoint is being used as a tool an AI agent calls directly: every "what's BTCUSDT's D1 trend right now?" question currently means fetching and transmitting the full ~200-asset universe just to find one row.

## What Changes

- Add an optional `symbols` query param to `GET /api/v1/signals` (comma-separated, e.g. `symbols=BTCUSDT,ETHUSDT`) that narrows the response to just the requested symbol(s).
- Symbol matching is silent/permissive, consistent with how `state`/`assetClass` already behave: an unknown or inactive symbol simply produces no matching row, never a 400/404. No new error path.
- No change to `GET /api/v1/assets/by-state` - browsing every asset in a given state is that endpoint's whole purpose, not a symbol lookup; `symbols` is added to `/api/v1/signals` only.

## Capabilities

### New Capabilities
(none - this extends an existing capability)

### Modified Capabilities
- `signals-query`: `GET /api/v1/signals` gains an optional `symbols` filter, usable alone or combined with `state`/`assetClass`/`sort`. No prior OpenSpec spec exists for this endpoint to diff against - it predates this repo's OpenSpec adoption - so `specs/signals-query/spec.md` in this change captures the endpoint's full current + new behavior as the baseline going forward.

## Impact

- Touches: `SignalQueryService.listSignals` (new parameter), `SignalsHandler.getSignals` (new query param + `@OpenApi` annotation), no DB schema change, no new DAO query - the symbol filter is applied in-memory against the same active-asset list `assetClassFilter` already produces.
- No change to response shape (`SignalListResponse`/`SignalSummary` unchanged) - purely a narrower result set.
- Companion effort (tracked in this change's tasks, not a separate spec since it changes no behavior): auditing and improving `@OpenApi` documentation across every existing endpoint, motivated by the same AI-agent-as-tool-caller use case - richer param/response descriptions reduce the chance of an agent guessing wrong about how to call an endpoint.
