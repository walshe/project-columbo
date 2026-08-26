## ADDED Requirements

### Requirement: SuperTrend default settings match the reference indicator
The system SHALL compute SuperTrend using an ATR length of 10 and a multiplier of 3.0 by default, matching the widely-used reference Pine Script v4 SuperTrend indicator's own defaults.

#### Scenario: Default settings are used for both persisted and provisional calculations
- **WHEN** `IndicatorComputationService` computes a persisted D1 or W1 SuperTrend value, or `ProvisionalTrendService` computes an unpersisted "as of today" read
- **THEN** both use ATR length 10 and multiplier 3.0

### Requirement: Trend-flip timing matches the reference indicator
The system SHALL determine a trend flip by comparing the current close against the *previous* bar's up/down bands, not the bands just recomputed for the current bar - matching the reference script's `close[1]`-relative `up1`/`dn1` semantics (`nz(up[1], up)`/`nz(dn[1], dn)`).

#### Scenario: A same-bar band reset does not cause an incorrect early flip
- **WHEN** a bar's own recomputed band would, taken alone, suggest a breakdown/breakout (e.g. the newly recomputed lower band rises above the current close) but the close remains on the correct side of the *previous* bar's band
- **THEN** the trend does not flip on that bar

#### Scenario: A genuine break of the previous bar's band flips the trend
- **WHEN** a bar's close crosses the previous bar's relevant band (above the previous down-band while in a downtrend, or below the previous up-band while in an uptrend)
- **THEN** the trend flips to the opposite direction on that bar

### Requirement: ATR uses Wilder smoothing
The system SHALL compute ATR using Wilder's smoothing method (equivalent to the reference script's `changeATR=true` / built-in `atr()` path), not a simple moving average of true range.

#### Scenario: ATR after warm-up uses the Wilder recurrence
- **WHEN** at least `atrLength` candles have been processed
- **THEN** each subsequent ATR value is computed as `(previousAtr * (atrLength - 1) + currentTrueRange) / atrLength`
