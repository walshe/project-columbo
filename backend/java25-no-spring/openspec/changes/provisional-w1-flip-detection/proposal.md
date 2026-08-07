## Why

Both weekly briefings (`weekly-trend-briefing`, `weekly-pullback-briefing`) only ever report the *committed* W1 trend state - the state as of last Friday's close. A trader reading either report on, say, a Wednesday has no way to know that this week's price action so far is already pointing toward a flip that won't become official until Friday. By the time the committed state updates, the move that mattered may already be over. Surfacing a provisional, week-to-date read - without waiting for the week to close - lets a trader see a flip coming rather than reading about it after the fact.

## What Changes

- Add a new computation that synthesizes a "week-to-date" W1 candle from this week's already-ingested D1 candles (Monday's open through today's latest close) and runs it through the existing SuperTrend calculation to produce a provisional trend direction and the exact flip-level price - entirely in-memory, computed fresh on every request.
- Compare the provisional read against the last *committed* `TrendState` for the same asset; only worth reporting when they disagree (i.e. a flip is forming).
- Surface this in both weekly briefings, differently per report:
  - `weekly-pullback-briefing`: inline per-candidate risk flag - if a candidate's committed W1 state is what the report is relying on, and the provisional W1 read is diverging from it, that undermines the report's core premise for that specific candidate and must be visible right next to it.
  - `weekly-trend-briefing`: a new, separate "Flips Forming" section - never blended into the existing confirmed confluence/scan lists, since that report's premise is "only back what's already confirmed."
  - The shared BTC Alignment section (used by both reports) gains BTC's own provisional W1 read alongside its existing committed W1/D1 states.

## Capabilities

### New Capabilities
- `provisional-w1-trend`: computing a week-to-date provisional W1 trend/flip read per asset (no persistence, no schema change), and surfacing it in both weekly briefing reports per the rules above.

### Modified Capabilities

(none - the weekly briefing endpoints were built directly, not through prior OpenSpec changes, so there is no existing spec to diff against; their new behavior is captured entirely within the new `provisional-w1-trend` capability above.)

## Impact

- New code only, no schema migration, no new persisted columns/tables, no changes to `signal_state`/`signal_event`/`candles`.
- Touches: a new service in the `signal` package (reads `CandleDao`, reuses the existing pure `SuperTrendCalculator`); the two existing weekly-briefing handlers (`WeeklyTrendBriefingHandler`, `WeeklyPullbackBriefingHandler`) and their formatters; the shared `WeeklyBriefingFormatting`/`WeeklyBriefingSignals` helpers used by both.
- Explicitly out of scope: provisional D1 (or any intraday) flip detection - that needs genuinely new intraday ingestion, a different capability not being built here.
