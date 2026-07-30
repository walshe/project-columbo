## ADDED Requirements

### Requirement: SuperTrend computed from finalized candles only
The system SHALL compute SuperTrend (True Range, Wilder ATR, basic/final bands, direction) from an ordered oldest-to-newest sequence of finalized candles for a single asset and timeframe, using `atrLength=10` and `multiplier=2.0` as fixed defaults. The system SHALL never compute SuperTrend using a candle whose close time has not yet finalized (i.e. is on or after the current UTC-midnight boundary).

#### Scenario: Full calculation over a candle history
- **WHEN** SuperTrend is calculated over an ordered list of finalized candles for one asset/timeframe
- **THEN** the system returns one `SuperTrendResult` per input candle, each with ATR, upper band, lower band, SuperTrend value, and direction (`UP` or `DOWN`)

#### Scenario: Partial/unfinalized candle excluded
- **WHEN** the latest fetched candle's close time is on or after the current UTC-midnight boundary
- **THEN** that candle is excluded from the input to SuperTrend calculation entirely

### Requirement: Deterministic, precise arithmetic
All SuperTrend arithmetic SHALL use `BigDecimal` (no floating point), with a fixed scale and `HALF_UP` rounding, so that re-running the calculation over identical input always produces identical output.

#### Scenario: Repeated calculation is stable
- **WHEN** SuperTrend is calculated twice over the exact same ordered candle input
- **THEN** both runs produce byte-identical `SuperTrendResult` values for every candle

### Requirement: Final band stickiness
The system SHALL compute final upper/lower bands using the standard "sticky" rule: the final upper band only decreases toward a new basic upper band if the new value is lower than the previous final upper band, or if the previous close broke above the previous final upper band (symmetric logic for the final lower band).

#### Scenario: Final upper band holds when basic upper band rises
- **WHEN** the newly computed basic upper band is higher than the previous final upper band, and the previous close did not break above the previous final upper band
- **THEN** the final upper band stays at its previous value rather than adopting the higher basic value

### Requirement: Direction flip detection
The system SHALL flip the SuperTrend value from the upper band to the lower band (or vice versa) exactly when price crosses the currently-active band, and SHALL report `direction=UP` when SuperTrend equals the final lower band, `direction=DOWN` when it equals the final upper band.

#### Scenario: Flip from downtrend to uptrend
- **WHEN** SuperTrend is on the upper band (downtrend) and the close price closes above the final upper band
- **THEN** SuperTrend flips to the final lower band for that candle and `direction=UP`

### Requirement: Incremental recomputation with warm-up window
Given a `lastStoredCloseTime` anchor, the system SHALL support recomputing SuperTrend only for candles strictly after the anchor, by internally re-running the full calculation over a warm-up window of `atrLength * 10` candles before the anchor (to let Wilder ATR restabilize) and discarding warm-up-window results from the returned output. The system SHALL also support a full-recalculation mode that recomputes every candle regardless of any stored anchor.

#### Scenario: Incremental recompute returns only new results
- **WHEN** SuperTrend is recomputed incrementally with a given `lastStoredCloseTime` anchor
- **THEN** the returned results contain only candles with close time strictly after the anchor, computed using a warm-up window of `atrLength * 10` prior candles

#### Scenario: Full recalculation ignores any anchor
- **WHEN** full recalculation mode is requested
- **THEN** the system returns a `SuperTrendResult` for every finalized candle in the input, regardless of any previously stored value

### Requirement: Timeframe-agnostic computation
The SuperTrend calculation SHALL apply identically to both D1 (daily) and W1 (weekly) candle series — the same algorithm, defaults, and precision rules, differing only in which candle series is supplied as input.

#### Scenario: W1 calculation uses the same algorithm as D1
- **WHEN** SuperTrend is calculated over a W1 candle series for an asset
- **THEN** the result uses the same ATR/band/flip logic and `atrLength=10`/`multiplier=2.0` defaults as the D1 calculation
