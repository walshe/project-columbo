## ADDED Requirements

### Requirement: Candle volume is dollar-notional value, consistent across providers
The system SHALL store each candle's `volume` as the dollar-notional value traded (price times shares/units traded) regardless of which provider it was sourced from, so that a cross-provider comparison or aggregation of `volume` (e.g. a liquidity ranking) is meaningful.

#### Scenario: A Tiingo-sourced candle's volume is dollar-notional, not a raw share count
- **WHEN** a daily candle is ingested for a Tiingo-sourced (`EXCHANGE`-venue) asset
- **THEN** its stored `volume` equals the split/dividend-adjusted shares traded multiplied by the split/dividend-adjusted close price, not the raw share count alone

#### Scenario: Historical Tiingo candles are corrected to the same convention
- **WHEN** a Tiingo-sourced candle was ingested before this convention was established
- **THEN** its stored `volume` is corrected (via a one-time migration) to the dollar-notional value derived from its own already-adjusted stored close price
