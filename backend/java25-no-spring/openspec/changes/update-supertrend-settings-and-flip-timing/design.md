## Context

`SuperTrendCalculator` is a pure, stateless calculator: True Range -> Wilder ATR -> bands -> trend direction, called by `IndicatorComputationService` (persisted D1/W1 indicator rows) and `ProvisionalTrendService` (unpersisted "as of today" reads for weekly briefings). Both callers use `SuperTrendCalculator.DEFAULT_ATR_LENGTH`/`DEFAULT_MULTIPLIER` rather than hardcoding settings themselves, so changing the constants is sufficient to retune both call sites.

The user supplied the exact reference Pine Script v4 SuperTrend indicator (the widely-known "KivancOzbilgic" version - `study("Supertrend", ...)`, defaults ATR period 10 / multiplier 3.0) and asked for both the new settings and matching logic.

## Goals / Non-Goals

**Goals:**
- ATR length 10, multiplier 3.0, matching the reference script's defaults exactly.
- Band computation and trend-flip timing matches the reference script bar-for-bar, including its specific choice to test flips against the previous bar's bands.

**Non-Goals:**
- No change to True Range or ATR method - the reference script's `changeATR=true` default already uses Wilder/RMA-style smoothing, which is what this calculator already implemented.
- No change to `SuperTrendResult`'s shape, `SuperTrendCalculator`'s public method signatures, or any caller's code beyond the constant value.
- No new "force full recompute" API/trigger - the existing `calculateIncremental` already has a full-calculate path (no prior stored value), which a one-time manual table clear reaches without new code.

## Decisions

**1. Rewrite the band/trend logic around explicit `up`/`dn` state, mirroring the reference script's own variable names and ternaries, rather than patching the existing `computeFinalUpperBand`/`computeFinalLowerBand`/`computeSuperTrendValue` methods in place.**

The old methods encoded trend direction implicitly (by testing whether `prevSuperTrend` equals `prevFinalUpper`) and, critically, tested `close` against bands *just computed for the current bar* inside `computeSuperTrendValue`. The reference script's trend-flip line is:

```
trend := trend == -1 and close > dn1 ? 1 : trend == 1 and close < up1 ? -1 : trend
```

where `up1 = nz(up[1], up)` and `dn1 = nz(dn[1], dn)` - **explicitly the previous bar's** band values (falling back to the current bar's own value only when there's no history at all, i.e. the very first bar). The two only disagree when a band "resets" (per its own ratchet rule) on the same bar the flip test runs - see the hand-traced example below. A direct, literal transliteration (explicit `up`/`dn`/`direction` state carried bar-to-bar, exactly mirroring the script's `up`/`dn`/`trend`) was chosen over trying to patch the old implicit-direction approach, since the old approach's whole shape (deriving direction from which band the supertrend value equals, using this bar's bands) is what caused the mismatch in the first place.

**Concrete divergence (verified by hand-trace, now a regression test - `trendFlipComparesAgainstThePreviousBarsBandNotTheCurrentlyRecomputedOne`):** Bar 0 establishes an uptrend with `up1 = 106`. Bar 1's own recomputed lower band comes out to `108` (above bar 1's own close of `107`) - comparing `close` against *that* value looks like a breakdown (`107 < 108`) and would flip to DOWN. But `close` (107) is still above bar 0's *carried* band (106), so the reference script - and the correct result - stays UP. The old code produced the wrong (DOWN) answer here; the rewrite produces UP.

- *Alternative considered:* keep the old implicit-direction structure and just swap which band value `computeSuperTrendValue` compares against. Rejected - the old structure conflates "which band does the supertrend line currently sit on" with "what value should the flip test use," and untangling those inside the existing method shape was more error-prone than a direct rewrite matching the reference script's own explicit state variables one-to-one.

**2. No first-bar special case needed.** The reference script's `nz(up[1], up)` self-reference (falling back to the current bar's own value when there's no bar 0) and `trend = 1` literal init are reproduced directly (`up1 = prevUp != null ? prevUp : up`, default direction `UP`) rather than as a separate branch - tracing it through shows this already reproduces the old code's first-bar special case (`close < finalLower ? finalUpper : finalLower`) as a natural consequence, not a coincidence to special-case around.

## Risks / Trade-offs

- **[Risk] Every historical `indicator_supertrend` row (D1 and W1, all assets) is now stale relative to the new formula**, and nothing automatically reconciles it - `IndicatorComputationService` always computes incrementally forward from each asset's last stored close time. → Mitigation: none built into this change (out of scope per Non-Goals); documented as a required one-time manual step in proposal.md's Impact section (clear the table, let the next ingestion run's no-prior-data path recompute everything fresh).
- **[Trade-off] The corrected flip-timing can, in principle, delay a flip's detection by one bar** relative to the old (incorrect) behavior in the specific scenario where they disagree - a same-bar band reset coinciding with what would otherwise be a flip. This is intentional: it's what the reference script actually does, not a new bug. Accepted as the whole point of "same other logic as here."
- **[Trade-off] Multiplier 2.0 -> 3.0 widens the bands**, which will reduce flip frequency (fewer, later signals) system-wide the moment a full recompute runs. This is the explicitly requested settings change, not a side effect to mitigate.

## Migration Plan

No schema migration - pure calculation-logic change plus a constant value. Deployment-time step (not a Flyway migration): after this ships, run `DELETE FROM indicator_supertrend;` (or `TRUNCATE`) against the target database, then trigger a normal ingestion run so every asset's D1 and W1 SuperTrend history recomputes fresh under the new formula. Signal state (`signal_state` table) derived from the old indicator values will also be stale until that recompute completes and downstream signal detection re-runs on top of it - same ordering the system already relies on for any indicator backfill.

## Open Questions

None - the reference script fully specifies the intended behavior, and the divergence was confirmed (not just theorized) via hand-traced arithmetic now captured as a test.
