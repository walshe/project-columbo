# signal-pct-change Specification

## Purpose
TBD - created by archiving change enrich-supertrend-percentage-change-since-flip. Update Purpose after archive.
## Requirements
### Requirement: Signal results include percentage change since flip
Every `SignalStateDto` SHALL include a `pctChangeSinceFlip` field (`BigDecimal`, nullable) representing the percentage price change from the closing price of the flip candle to the most recent finalized candle close, computed as `((current − flip) / flip) × 100` and rounded to 2 decimal places. The value SHALL be positive when price has risen since the flip and negative when price has fallen. The field SHALL be `null` when no flip is recorded or when the candle at the flip time cannot be found.

#### Scenario: Bullish asset with price up since flip
- **WHEN** an asset flipped bullish with a close of 100.00 and the latest close is 115.00
- **THEN** `pctChangeSinceFlip` is `+15.00`

#### Scenario: Bearish asset with price down since flip
- **WHEN** an asset flipped bearish with a close of 200.00 and the latest close is 190.00
- **THEN** `pctChangeSinceFlip` is `-5.00`

#### Scenario: Bullish asset but price has fallen since flip
- **WHEN** an asset flipped bullish with a close of 100.00 and the latest close is 97.00
- **THEN** `pctChangeSinceFlip` is `-3.00`

#### Scenario: No flip recorded
- **WHEN** an asset has no recorded flip event
- **THEN** `pctChangeSinceFlip` is `null`

#### Scenario: Flip candle missing from database
- **WHEN** an asset has a recorded flip time but no candle exists at that timestamp
- **THEN** `pctChangeSinceFlip` is `null`

### Requirement: Percentage change is surfaced in Markdown reports
The Markdown formatters for the SuperTrend summary and the trend alignment report SHALL display `pctChangeSinceFlip` alongside the existing flip recency and volume when the value is non-null. The format SHALL be `+N.NN%` for positive values and `-N.NN%` for negative values.

#### Scenario: Positive change shown in summary Markdown
- **WHEN** `format=MARKDOWN` is requested and an asset has `pctChangeSinceFlip = 12.34`
- **THEN** the response contains `+12.34%`

#### Scenario: Negative change shown in summary Markdown
- **WHEN** `format=MARKDOWN` is requested and an asset has `pctChangeSinceFlip = -3.21`
- **THEN** the response contains `-3.21%`

#### Scenario: Null change omitted from Markdown
- **WHEN** `format=MARKDOWN` is requested and an asset has `pctChangeSinceFlip = null`
- **THEN** the response does not include a percentage change figure for that asset

