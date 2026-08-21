## 1. Shared week-aggregation extraction

- [x] 1.1 Extract `CandleRollupService`'s private `groupByWeek`/`aggregate` logic into a small shared, reusable form (widen visibility or move to a small new utility) so both the committed rollup and the new provisional computation use exactly one implementation
- [x] 1.2 Confirm `CandleRollupService`'s own behavior is unchanged after the extraction (existing `CandleRollupServiceTest` still passes untouched)

## 2. Provisional trend computation

- [x] 2.1 Add `ProvisionalTrendResult` (direction as `TrendState`, flip-level price as `BigDecimal`) to the `signal` package
- [x] 2.2 Add `ProvisionalTrendService` in the `signal` package: per active asset (optionally filtered by `AssetClass`), synthesize this week's week-to-date candle from D1 candles ingested since the current week's Monday, append it to the asset's committed W1 candle history, and run `SuperTrendCalculator.calculateIncremental` anchored at the last committed W1 close time to get the provisional result
- [x] 2.3 Use `ParallelAssetExecutor` for per-asset parallelism, one asset's failure caught/logged without affecting others, matching `IndicatorComputationService`'s existing pattern
- [x] 2.4 Handle the "no D1 candles ingested yet this week" case by omitting that asset from the result rather than erroring
- [x] 2.5 Unit-test the synthesis + SuperTrend hand-off with a pure in-memory candle fixture (no DB) - assert a known partial week produces the expected direction and flip-level price

## 3. Wiring into weekly-pullback-briefing

- [x] 3.1 In `WeeklyPullbackBriefingHandler`, compute provisional reads for the relevant asset universe and compare each pullback candidate's provisional W1 read against its committed W1 state
- [x] 3.2 Thread the divergence result into `WeeklyPullbackBriefingReport` (only for candidates where provisional differs from committed, per the divergence-only rule)
- [x] 3.3 Render the inline risk annotation in `WeeklyPullbackBriefingFormatter` next to any candidate with a diverging provisional read

## 4. Wiring into weekly-trend-briefing

- [x] 4.1 In `WeeklyTrendBriefingHandler`, compute provisional reads across the full active-asset universe (not just already-scanned candidates) per asset class
- [x] 4.2 Identify "Flips Forming" candidates: committed W1/D1 not confluence-eligible today, but provisional W1 now agrees with committed D1
- [x] 4.3 Add the new "Flips Forming" data to `WeeklyTrendBriefingReport`, kept structurally separate from the existing confluence/scan fields
- [x] 4.4 Render a new, separate "Flips Forming" section in `WeeklyTrendBriefingFormatter` - verify the existing confluence/scan sections are unchanged in content and order

## 5. Shared BTC Alignment extension

- [x] 5.1 Extend `WeeklyBriefingFormatting.appendBtcSection`/`WeeklyBriefingSignals` to accept and render BTC's provisional W1 read alongside its existing committed W1/D1 states
- [x] 5.2 Confirm both reports' BTC Alignment section picks up the change automatically (shared helper, no per-report duplication)

## 6. Verification

- [x] 6.1 `mvn compile` clean
- [x] 6.2 `mvn test` - full suite green, including new unit tests
- [x] 6.3 Run the app locally against real data; hit both `POST /weekly-trend-briefing` and `POST /weekly-pullback-briefing`; manually confirm the new sections/annotations render sensibly and existing sections are unaffected
- [x] 6.4 Confirm no new persisted rows appear in `signal_state`/`signal_event`/`candles` as a result of calling either endpoint (provisional data must not leak into committed storage) - verified `ProvisionalTrendService` contains no write calls at all

## 7. Documentation

- [x] 7.1 Update `backend/java25-no-spring/README.md`'s "Weekly briefings" section to describe the provisional W1 flip signal and how each report uses it differently
