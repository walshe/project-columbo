## Context

`ScanService.execute` already fetches `SignalSummary` candidates per condition via
`signalQueryService.listSignals(...)`, and `SignalSummary` already carries `avgVolume7d` (it's
what powers `/signals`' existing `LIQUIDITY_DESC` sort). `ScanService.matchesForCondition`
already reads one field off each candidate into a side map for later use - `assetClassBySymbol`,
populated during matching and consulted when building the final `ScanResult` list. No new query,
DAO, or database work is needed; this is purely a matter of carrying a value that's already in
hand one step further than it currently goes.

## Goals / Non-Goals

**Goals:**
- Surface `avgVolume7d` on every `ScanResult`.
- Let a caller request `LIQUIDITY_DESC` ordering on `/scan` results instead of the current
  fixed symbol-ascending order.

**Non-Goals:**
- No change to how `avgVolume7d` itself is computed (`v_asset_liquidity` view, `AssetLiquidityDao`
  stay untouched).
- No per-condition volume (a condition match already implicitly belongs to one asset; volume is
  a property of the asset, not of any one matched condition - it shouldn't be duplicated N times
  across N matched conditions for the same symbol).
- No ascending liquidity sort, matching `SignalSort`'s existing precedent and rationale (liquidity
  sort exists to surface the most-tradeable matches first; nothing needs the opposite).

## Decisions

**`avgVolume7d` lives on `ScanResult`, not `ScanConditionMatch`.** A `ScanResult` already groups
one symbol's matched conditions (`List<ScanConditionMatch> matchedConditions`); volume is a
property of the asset, computed once regardless of how many conditions it matched. Putting it on
`ScanConditionMatch` instead would mean an asset matching 2 AND-combined conditions reports the
same number twice - redundant and a source of drift if the two copies were ever computed at
slightly different times.

**Carried the same way `assetClass` already is.** `matchesForCondition` already populates
`assetClassBySymbol` (a `Map<String, AssetClass>`) as a side effect of iterating candidates, read
back when building `ScanResult`s. `avgVolumeBySymbol` follows the identical pattern - one more
`Map<String, BigDecimal>` populated in the same loop, read back in the same place. No new
abstraction, no second pass over the candidates.

**New `ScanSort` enum, not a boolean.** Mirrors `SignalSort`'s shape and its own documented
rationale (`SYMBOL_ASC` current-default, `LIQUIDITY_DESC` the only other option) rather than
introducing a `sortByLiquidity: boolean` field - keeps the two sort concepts in this codebase
consistent in shape, and leaves room to add another `ScanSort` value later without a breaking
field-type change. Lives in the `signal` package alongside `SignalSort`, not merged into it -
`/scan` and `/signals` are independent capabilities per `ScanService`'s own class-level javadoc
("a standalone capability"), so their sort enums stay independent too.

**Sort applied after the existing symbol-ascending default, before `limit` truncation.**
`ScanService.combine` currently does `.sorted(Comparator.comparing(ScanResult::symbol))` then
truncates by `request.limit()` if set. The new sort branches on `request.sort()` in that same
spot (`SYMBOL_ASC`/null → today's behavior, `LIQUIDITY_DESC` → `Comparator.comparing(ScanResult::avgVolume7d).reversed()`)
so `limit` continues to apply to the *sorted* list either way - a `limit` after a liquidity sort
should return the N most liquid matches, not an arbitrary N then sorted.

## Risks / Trade-offs

[Existing `/scan` callers with a hardcoded assumption about symbol-ascending ordering] →
Mitigated by defaulting `sort` to the current behavior when omitted from the request body; this
is purely additive, no existing request/response shape is removed or renamed.

[`avgVolume7d` defaults to `BigDecimal.ZERO` when a `v_asset_liquidity` row is missing, same as
`/signals`] → Already the existing, tested behavior of `AssetLiquidityDao`/`SignalSummary` - not
a new edge case introduced by this change, just now also visible through `/scan`.
