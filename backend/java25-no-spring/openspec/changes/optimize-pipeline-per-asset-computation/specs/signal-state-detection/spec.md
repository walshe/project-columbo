## ADDED Requirements

### Requirement: Signal-state detection recomputes over a bounded warm-up window
The system SHALL derive signal state and flip events for an asset by recomputing SuperTrend over a bounded candle window — a warm-up window of `atrLength * 10` candles immediately before the asset's last stored signal-state close time, plus every finalized candle after it — rather than over the asset's full candle history. The trend state carried into the first post-anchor candle SHALL be the fully-established state computed from the warm-up window, so a flip landing exactly on the first new candle is still detected. When fewer than `atrLength * 10` candles precede the anchor, or no signal-state row is stored yet, the system SHALL recompute over the entire available history.

#### Scenario: Bounded window load for incremental detection
- **WHEN** signal-state detection runs for an asset that has a stored signal-state row with at least `atrLength * 10` candles before it
- **THEN** the number of candle rows read is bounded to the warm-up window plus the candles after the stored close time, not the asset's full history

#### Scenario: Flip on the first new candle is detected
- **WHEN** SuperTrend direction changes between the asset's last stored signal-state close time and the immediately following finalized close time
- **THEN** a reversal event is recorded at that following close time, identical to what a full-history recompute would produce

#### Scenario: Bounded detection matches full-history detection
- **WHEN** signal state is derived for a candle at or after a mid-series anchor, once via full-history recompute and once via the bounded warm-up-window path, over the same append-only candle history
- **THEN** the derived `SignalState` (trend state and event) is identical for that candle

#### Scenario: No stored row falls back to full history
- **WHEN** an asset has candle history but no stored signal-state row
- **THEN** signal-state detection recomputes over the entire available history for that asset and timeframe

### Requirement: Per-asset signal-state detection is skipped when no new finalized candle exists
During a pipeline run, the system SHALL skip an asset's signal-state detection entirely — no candle load, no recomputation, no upsert — when the asset already has a stored signal-state row and its latest finalized candle close time is not after that row's close time. An asset with no stored signal-state row SHALL always be processed.

#### Scenario: Unchanged asset is skipped
- **WHEN** an asset's latest finalized candle close time equals its latest stored signal-state close time
- **THEN** no candle rows are read, no recomputation runs, and no upsert is attempted for that asset in that run

#### Scenario: Asset with a new candle is processed
- **WHEN** an asset's latest finalized candle close time is after its latest stored signal-state close time
- **THEN** signal-state detection runs for the new candle(s), including any flip event on them

#### Scenario: Previously errored asset catches up
- **WHEN** an asset failed to persist a signal-state row on a prior run and now has finalized candles after its last successfully stored row
- **THEN** the asset is processed on the current run rather than skipped
