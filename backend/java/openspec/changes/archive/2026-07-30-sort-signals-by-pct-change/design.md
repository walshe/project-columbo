## Context

`SignalQueryService.listSignals` already applies an optional `SignalSort` via a `switch` that builds a `Comparator<SignalStateDto>` and sorts the result list in memory. Existing options: `ASSET_ASC`, `LAST_FLIP_ASC/DESC`, `TREND_STATE_ASC`, `LIQUIDITY_DESC`. Every `SignalStateDto` now carries `pctChangeSinceFlip` (`BigDecimal`, nullable), populated from the flip-candle close and the latest candle close. The `/api/v1/signals` controller binds its `sort` query param directly to the `SignalSort` enum, so new enum constants become accepted values with no controller change.

## Goals / Non-Goals

**Goals:**
- Let callers order `/api/v1/signals` by `pctChangeSinceFlip` in either direction
- Keep the signed value meaningful — the caller expresses "trend-confirming" intent by choosing the direction, not the service
- Handle `null` (no flip data) predictably: always last

**Non-Goals:**
- Changing `/api/v1/summary` or `/api/v1/summary/trend-alignment` ordering
- Server-side "absolute magnitude" sorting (`abs(pct)`) — mixes confirming moves with counter-trend bounces; rejected in favour of signed + caller-chosen direction
- Auto-selecting sort direction from the trend-state filter
- A new curated "biggest movers" report endpoint (possible future follow-up, out of scope here)

## Decisions

**Two signed enum values, not one abs() sort**
Add `PCT_CHANGE_ASC` and `PCT_CHANGE_DESC`. Because `pctChangeSinceFlip` is signed, a bullish list wants `DESC` (biggest gain first) and a bearish list wants `ASC` (biggest drop first). Since `/api/v1/signals` filters by a single `state`, the caller already knows which list they're viewing and picks the matching direction — so no per-state direction logic is needed in the service. *Alternative*: a single `PCT_CHANGE` that sorts by `abs()` — rejected because a bearish asset up +5% against its signal is a failing signal, not a "big mover", and absolute magnitude would rank it as one.

**Nulls last via `Comparator.nullsLast`**
Mirror the existing `LAST_FLIP_ASC/DESC` treatment: `Comparator.comparing(SignalStateDto::pctChangeSinceFlip, Comparator.nullsLast(...))`. A `null` pct means "no flip data", which must not be conflated with `0.00` (flat since flip). This differs from `LIQUIDITY_DESC`, which coalesces null to `BigDecimal.ZERO` — appropriate there (no volume ≈ zero volume) but wrong here.

**In-memory comparator, consistent with existing sorts**
Reuse the current post-query `dtos.sort(comparator)` path — no new query, no DB-level ordering. The universe is ~45 assets, already fully materialized for the other in-memory sorts.

## Risks / Trade-offs

[Caller picks the "wrong" direction for a list] A user could request `PCT_CHANGE_DESC` on a bearish filter and see the weakest declines first. → Acceptable: the semantics are documented on the enum, and both directions are legitimate depending on intent. The endpoint stays a general query surface rather than an opinionated report.

[Signed vs. absolute could surprise someone expecting "biggest mover either way"] → Documented in the enum comment and spec. If an absolute-magnitude view is later wanted, it belongs in the planned curated report endpoint, not here.
