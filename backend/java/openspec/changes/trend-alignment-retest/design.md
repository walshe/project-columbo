## Context

The current `/api/v1/summary/confluence` endpoint intersects W1 and D1 SuperTrend signal lists and returns only assets currently aligned on both timeframes. The endpoint is implemented in `ConfluenceSummaryService`, which calls `SignalQueryService.listSignals` twice and intersects by symbol.

A retest setup occurs when the W1 trend is intact but the D1 has briefly flipped counter-trend — this is a common continuation pattern. If the D1 has been counter-trend for too long (> `maxRetestAgeDays`) it is no longer considered a retest; the asset has diverged and may rejoin the confluence list on its own.

## Goals / Non-Goals

**Goals:**
- Rename the endpoint to `/api/v1/summary/trend-alignment` (breaking change)
- Add bull retest and bear retest lists to the existing two-section response
- Accept `maxRetestAgeDays` query param (default 7) to define the retest window
- Keep implementation entirely in the application layer — no new DB queries needed

**Non-Goals:**
- Changing SuperTrend calculation or signal storage
- Adding pagination or filtering beyond `maxRetestAgeDays`
- Deprecation redirect from the old `/confluence` path (callers must update)

## Decisions

**Retest detection in application layer**
`SignalQueryService.listSignals` already returns the current trend state per asset. To detect a retest, fetch the W1 aligned list and the D1 *counter-trend* list, intersect by symbol, and filter to assets whose D1 `lastFlipTime` is within `maxRetestAgeDays`. No new repository queries needed; all data is already fetched.

*Alternative considered*: a dedicated DB query joining W1 and D1 signal states directly — rejected because the current data set is small (tens to low hundreds of assets) and the in-memory approach keeps the query model simple.

**`maxRetestAgeDays` as a query param with a hardcoded default**
Defaulting to 7 in the controller matches the user's stated expectation ("if counter-trend for more than 7 days, treat as diverged"). Making it a query param lets callers experiment without a redeploy.

*Alternative considered*: making it a config property — rejected because it's a per-call concern, not a system-wide setting.

**Single DTO extended, not a new DTO**
`ConfluenceSummaryReport` gains `bullishRetest` and `bearishRetest` fields. This keeps the response shape consistent and avoids a second endpoint.

**Rename is breaking — no redirect**
The old path is only consumed by the Telegram bot and the `curl`-based manual tests. A redirect would mask the breaking change and complicate future cleanup.

## Risks / Trade-offs

[Retest window edge case] An asset that flipped D1 counter-trend exactly on day 7 may appear in the retest list on some runs and not others depending on the time of the API call vs. candle close. → Acceptable; the window is advisory, not a hard guarantee.

[Breaking rename] Any caller not updated will get 404. → Scope is small (internal only); document in PR description.

## Migration Plan

1. Deploy new code (endpoint live at new path, old path returns 404)
2. Update Telegram bot command to call `/api/v1/summary/trend-alignment`
3. No DB migration needed
