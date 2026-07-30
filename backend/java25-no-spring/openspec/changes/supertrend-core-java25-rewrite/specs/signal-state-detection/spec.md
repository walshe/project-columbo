## ADDED Requirements

### Requirement: SuperTrend direction mapped to bullish/bearish signal state
The system SHALL derive a signal state per asset/timeframe/close-time from the SuperTrend direction: `UP` maps to `BULLISH`, `DOWN` maps to `BEARISH`. Assets with candle history but not yet enough finalized candles for SuperTrend to produce a value SHALL be assigned an `UNKNOWN` state rather than being omitted.

#### Scenario: Uptrend maps to bullish state
- **WHEN** SuperTrend direction is `UP` for an asset/timeframe/close-time
- **THEN** the derived signal state is `BULLISH`

#### Scenario: Insufficient history maps to unknown state
- **WHEN** an asset has candles but not enough finalized history for SuperTrend to warm up
- **THEN** the derived signal state is `UNKNOWN`, not omitted from signal-state output

### Requirement: Flip event emitted exactly on direction change
The system SHALL emit a bullish-reversal or bearish-reversal signal event exactly on the close-time where SuperTrend direction changes from the immediately preceding finalized close, and SHALL emit no event (`NONE`) on close-times where direction is unchanged.

#### Scenario: Reversal event on direction change
- **WHEN** SuperTrend direction changes from `DOWN` to `UP` between two consecutive finalized close-times for an asset/timeframe
- **THEN** a bullish-reversal event is recorded at the later close-time

#### Scenario: No event when direction is unchanged
- **WHEN** SuperTrend direction is the same on two consecutive finalized close-times
- **THEN** no signal event is recorded for the later close-time (event = `NONE`)

### Requirement: Single-indicator signal state schema
Signal state SHALL be keyed by `(asset, timeframe, close_time)` only — the schema SHALL NOT include an indicator-type dimension, since SuperTrend is the only indicator in this system.

#### Scenario: Unique signal state per asset/timeframe/close-time
- **WHEN** signal state is persisted for an asset/timeframe/close-time that already has a stored row
- **THEN** the existing row is updated in place rather than a second row being inserted for the same key
