## MODIFIED Requirements

### Requirement: Incremental recomputation with warm-up window
Given a `lastStoredCloseTime` anchor, the system SHALL recompute SuperTrend only for candles strictly after the anchor, by re-running the calculation over a warm-up window of `atrLength * 10` candles immediately before the anchor (to let Wilder ATR and the band/direction recurrence restabilize) and discarding warm-up-window results from the returned output. The system SHALL load only that warm-up window plus the post-anchor candles from storage — it SHALL NOT read the asset's full candle history when an anchor exists. When fewer than `atrLength * 10` candles precede the anchor, the system SHALL fall back to loading and recomputing the entire available history. The system SHALL also support a full-recalculation mode that loads and recomputes every candle regardless of any stored anchor.

#### Scenario: Incremental recompute returns only new results
- **WHEN** SuperTrend is recomputed incrementally with a given `lastStoredCloseTime` anchor
- **THEN** the returned results contain only candles with close time strictly after the anchor, computed using a warm-up window of `atrLength * 10` prior candles

#### Scenario: Bounded candle load for incremental recompute
- **WHEN** an asset's incremental SuperTrend recomputation runs and an anchor exists with at least `atrLength * 10` candles before it
- **THEN** the number of candle rows read from storage is bounded to the warm-up window plus the candles after the anchor, not the asset's full history

#### Scenario: Insufficient pre-anchor history falls back to full load
- **WHEN** an anchor exists but fewer than `atrLength * 10` candles precede it
- **THEN** the system loads and recomputes over the entire available candle history for that asset and timeframe

#### Scenario: Bounding the candle load does not change the stored result
- **WHEN** an asset's incremental SuperTrend recomputation runs once with the candle load bounded to the warm-up window and once with the full candle history loaded, over the same append-only history
- **THEN** the persisted `SuperTrendResult` for every candle after the anchor is byte-identical between the two runs (the incremental recomputation already restricts its own working window, so a smaller input list that still covers that window produces the same output)

#### Scenario: Full recalculation ignores any anchor
- **WHEN** full recalculation mode is requested
- **THEN** the system returns a `SuperTrendResult` for every finalized candle in the input, regardless of any previously stored value

## ADDED Requirements

### Requirement: Per-asset indicator computation is skipped when no new finalized candle exists
During a pipeline run, the system SHALL skip an asset's SuperTrend indicator computation entirely — performing no candle load, no recomputation, and no upsert — when the asset already has a stored indicator value and its latest finalized candle close time is not after that stored value's close time. An asset with no stored indicator value SHALL always be computed.

#### Scenario: Unchanged asset is skipped
- **WHEN** an asset's latest finalized candle close time equals its latest stored SuperTrend indicator close time
- **THEN** no candle rows are read, no recomputation runs, and no upsert is attempted for that asset in that run

#### Scenario: Asset with a new candle is computed
- **WHEN** an asset's latest finalized candle close time is after its latest stored SuperTrend indicator close time
- **THEN** the asset's incremental recomputation runs for the new candle(s)

#### Scenario: First-ever computation is never skipped
- **WHEN** an asset has candle history but no stored SuperTrend indicator value yet
- **THEN** the asset is computed (full recompute over available history), not skipped

#### Scenario: Previously errored asset catches up
- **WHEN** an asset failed to persist an indicator value on a prior run and now has finalized candles after its last successfully stored value
- **THEN** the asset is computed on the current run rather than skipped
