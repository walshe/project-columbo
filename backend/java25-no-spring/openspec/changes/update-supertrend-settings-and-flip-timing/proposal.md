## Why

The user requested changing SuperTrend's settings from (ATR length 10, multiplier 2.0) to (10, 3.0) to match the widely-used "KivancOzbilgic" reference Pine Script v4 SuperTrend indicator, and asked for "same other logic as here." Comparing `SuperTrendCalculator` against that script line-by-line surfaced a real behavioral divergence, not just a settings difference: the calculator determined trend flips by comparing `close` against the bands *just recomputed for the current bar*, while the reference script compares against the *previous* bar's bands (`up1`/`dn1`, its `nz(up[1], up)` idiom). The two agree in the vast majority of cases but diverge whenever a band "resets" (ratchets to a less favorable value) on the same bar a flip would otherwise trigger - confirmed with a concrete hand-traced counter-example, not just by inspection.

## What Changes

- `SuperTrendCalculator.DEFAULT_MULTIPLIER`: `2.0` -> `3.0`. `DEFAULT_ATR_LENGTH` (10) is unchanged - it already matched.
- Rewrites `SuperTrendCalculator`'s internal band/trend-flip logic to match the reference script bar-for-bar: explicit `up`/`dn` bands with Pine's exact ratchet ternaries, and a trend-flip test that compares `close` against the *previous* bar's bands, not the current bar's. External shape (`calculate`/`calculateIncremental` signatures, `SuperTrendResult`'s fields) is unchanged - this is an internal correctness fix, not an API change.
- No change to True Range or Wilder ATR computation - already confirmed correct (matches the reference script's default `changeATR=true` path) and untouched by this fix.
- **Operational note, not a code change**: every existing `indicator_supertrend` row was computed under the old multiplier and the old (occasionally-wrong) flip timing. `IndicatorComputationService` only ever computes incrementally forward from each asset's last stored value (`fullRecalc` is hardcoded `false`, never exposed via the API) - it will not automatically reconcile old rows to the new formula. A one-time full recompute (e.g. clearing the `indicator_supertrend` table so the next ingestion run's `calculateIncremental` naturally takes its no-prior-data full-calculate path) is needed after this ships; see Impact.

## Capabilities

### New Capabilities
- `supertrend-calculation`: the SuperTrend indicator's calculation rules (ATR method, default settings, band/trend-flip logic) as their own tracked capability - not previously captured as a formal spec.

### Modified Capabilities
(none tracked as formal specs yet for indicator calculation - captured here as a new capability instead, see specs/)

## Impact

- `SuperTrendCalculator.java` (main change), `SuperTrendCalculatorTest.java` (new regression test locking in the previous-bar-reference behavior; all prior tests pass unchanged - the existing hand-verified scenarios never happened to hit the divergent case).
- `IndicatorComputationService`/`ProvisionalTrendService` both reference `SuperTrendCalculator.DEFAULT_ATR_LENGTH`/`DEFAULT_MULTIPLIER` as constants - pick up the new multiplier automatically, no code change needed there.
- Operational: every stored `indicator_supertrend` row (D1 and W1, every asset) is stale relative to the new formula until a full recompute runs. Recommended: `TRUNCATE TABLE indicator_supertrend;` (or `DELETE FROM indicator_supertrend;`) against the target database, then trigger a normal ingestion run - `calculateIncremental` already takes its full-calculate path whenever no prior stored value exists for an asset, no new recompute-trigger code needed.
- No schema/migration change - this is pure calculation logic.
