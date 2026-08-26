## 1. Settings + logic fix

- [x] 1.1 Change `SuperTrendCalculator.DEFAULT_MULTIPLIER` from `2.0` to `3.0`
- [x] 1.2 Rewrite `calculate`'s band/trend logic to explicit `up`/`dn`/`direction` state matching the reference Pine script, replacing the old implicit-direction `computeFinalUpperBand`/`computeFinalLowerBand`/`computeSuperTrendValue` methods with `computeUpBand`/`computeDnBand`/`computeDirection`
- [x] 1.3 Verify trend-flip test compares `close` against the *previous* bar's bands, not the current bar's (the actual bug found)

## 2. Tests

- [x] 2.1 Confirm all existing `SuperTrendCalculatorTest` cases pass unchanged against the rewrite (they do - none of the hand-verified scenarios happen to hit the divergent case)
- [x] 2.2 Add a new regression test hand-tracing the specific divergence (same-bar band reset coinciding with a would-be flip) and asserting the reference-script-correct outcome
- [x] 2.3 Full non-e2e suite green (270 tests) - confirms no other test's synthetic data was tuned tightly enough to the old multiplier/timing to break

## 3. Documentation / operational follow-up

- [x] 3.1 Document the required one-time production recompute (clear `indicator_supertrend`, re-run ingestion) in proposal.md/design.md - no new recompute-trigger code needed, existing no-prior-data path already does a full recalc
