## ADDED Requirements

### Requirement: Verified TradingView reference for real-equity assets
The system SHALL store a verified TradingView `EXCHANGE:SYMBOL` reference (`tradingview_ref`) for an `EXCHANGE`-venue asset, distinct from and not derived from that asset's `symbol` or its source provider's own exchange metadata at read time. The stored reference SHALL be individually verified against TradingView's own symbol resolution before being seeded, not inferred from a general mapping table.

#### Scenario: A ticker's provider format differs from TradingView's own format
- **WHEN** an asset's tradeable symbol at its data provider does not match TradingView's symbol format for the same company (e.g. a dash where TradingView uses a dot)
- **THEN** the stored `tradingview_ref` reflects TradingView's actual format, not the provider's

#### Scenario: An OTC or foreign-exchange listing is stored under its real exchange
- **WHEN** an `EXCHANGE`-venue asset trades as a US OTC ADR or on a non-US exchange
- **THEN** its `tradingview_ref` uses that listing's real TradingView exchange prefix (e.g. `OTC`, `SSE`), not a guessed or default one

### Requirement: Chart link generation uses the verified reference for EXCHANGE-venue assets
The system SHALL build an `EXCHANGE`-venue asset's TradingView chart deep link directly from its stored `tradingview_ref` when present. The system SHALL return no chart link (rather than a fabricated one) for an `EXCHANGE`-venue asset with no stored `tradingview_ref`. Chart link generation for `SPOT`/`FUTURES`-venue assets SHALL be unaffected by this requirement.

#### Scenario: An EXCHANGE-venue asset with a verified reference gets a working chart link
- **WHEN** a signal or scan result includes an `EXCHANGE`-venue asset that has a stored `tradingview_ref`
- **THEN** its `tradingviewUrl` points at that exact TradingView symbol

#### Scenario: An EXCHANGE-venue asset without a verified reference gets no chart link
- **WHEN** a signal or scan result includes an `EXCHANGE`-venue asset with no stored `tradingview_ref`
- **THEN** its `tradingviewUrl` is null, not a fabricated or default-exchange link

#### Scenario: A Binance-sourced asset is unaffected
- **WHEN** a signal or scan result includes a `SPOT`- or `FUTURES`-venue asset
- **THEN** its `tradingviewUrl` is built exactly as before, regardless of whether a `tradingview_ref` happens to be present
