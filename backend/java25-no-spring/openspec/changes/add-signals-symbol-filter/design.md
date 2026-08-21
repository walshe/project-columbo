## Context

`SignalQueryService.listSignals` already builds `activeAssetsById` from `assetDao.findAllActive(assetClassFilter)` and applies `stateFilter` as an in-memory predicate inside `summarize`. Both existing filters narrow a list that's already fully fetched - the active-asset universe is on the order of ~200 rows, not large enough to justify pushing every filter into SQL.

This work is prompted by using `/api/v1/signals` as a tool an AI agent calls directly. The agent typically wants "trend state for BTCUSDT on W1," not "trend state for all 200 active assets, which I'll then search for BTCUSDT." Returning the full list for a single-symbol question wastes both request latency and, more importantly, the calling agent's context budget on every call - the exact problem this change and its companion documentation pass both address.

## Goals / Non-Goals

**Goals:**
- Let a caller narrow `/api/v1/signals` to one or more specific symbols in a single request.
- Keep the existing filters (`state`, `assetClass`, `sort`) fully composable with the new `symbols` filter.
- Match the endpoint's existing silent-filter behavior exactly - no new error/validation path for unknown symbols.

**Non-Goals:**
- No new endpoint, no change to `/api/v1/assets/by-state`.
- No case-insensitive or fuzzy symbol matching - exact match against the stored symbol string, same as every other symbol comparison in this codebase.
- No limit on how many symbols can be passed. If this becomes a real problem in practice, YAGNI applies; not solving a hypothetical.

## Decisions

**Filter in-memory against the already-fetched active-asset list, no new DAO query.** Symmetric with how `stateFilter` already works. Given `assetClassFilter` already produces a right-sized (~200-row) `activeAssetsById` map before any DB-side symbol filtering could help, pushing `symbols` down to SQL would add a second code path (a parameterized `IN (...)` query) for no measurable benefit. *Alternative considered:* add `AssetDao.findAllActive(assetClassFilter, symbolFilter)` - rejected as premature optimization for a list this small.

**Parse `symbols` as a comma-separated string into a `Set<String>` in the handler, pass it through as a plain filter parameter**, mirroring how `state`/`assetClass` are already single enums parsed by Javalin's `queryParamAsClass`. Javalin has no built-in `Set<String>` query param binding, so comma-splitting happens in `SignalsHandler`, producing an ordinary `Set<String>` (or `null` when the param is absent) passed into `SignalQueryService.listSignals`.

**Symbol match applied at the same point `stateFilter` is applied in `summarize`** - a candidate is skipped when `symbolFilter != null && !symbolFilter.contains(candidate.symbol())`. Keeps `summarize`'s existing pure/testable shape: one more optional predicate, no restructuring.

## Risks / Trade-offs

- **[Risk] Case sensitivity surprises a caller who passes `btcusdt` instead of `BTCUSDT`.** -> **Mitigation:** none planned. Every symbol in this system is already stored/returned in a single canonical case (uppercase, e.g. `BTCUSDT`), and no other part of the API does case-insensitive symbol matching; adding it here alone would be an inconsistency, not a fix. The `@OpenApi` param description will state the exact-case requirement explicitly.
- **[Risk] An AI agent tool-caller passes a slightly wrong symbol (e.g. `BTC` instead of `BTCUSDT`) and silently gets zero results instead of an error, making the mistake harder to notice.** -> **Mitigation:** matches this endpoint's existing behavior for `state`/`assetClass` filters already (no error on zero matches). The `@OpenApi` param description will explicitly document the exact-symbol-string requirement and give an example, so the calling agent has this hint up front - this is also the direct motivation for the companion annotation-documentation pass happening alongside this change.

## Migration Plan

No data migration. Purely additive query param; omitting it preserves today's exact behavior (all active assets returned, subject to existing filters). Safe to roll back by reverting the branch/PR.

## Open Questions

None.
