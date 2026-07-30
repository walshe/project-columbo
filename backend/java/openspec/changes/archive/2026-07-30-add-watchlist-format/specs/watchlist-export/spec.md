## ADDED Requirements

### Requirement: Reports can be returned as a TradingView watchlist
The `/api/v1/summary` and `/api/v1/summary/trend-alignment` endpoints SHALL accept `format=WATCHLIST` and return a TradingView-importable watchlist as `text/plain`. The body SHALL contain a `###`-prefixed section header for each report section that maps to a group of assets, followed by one `EXCHANGE:SYMBOL` line per asset in that section (e.g. `BINANCE:BTCUSDT`). The `EXCHANGE:SYMBOL` token SHALL match the exchange and symbol used in that asset's TradingView chart link.

#### Scenario: Trend-alignment as a watchlist
- **WHEN** `GET /api/v1/summary/trend-alignment?format=WATCHLIST` is requested and there are bullish-confluence and bearish-confluence assets
- **THEN** the response is `text/plain` containing a `###` header for each populated section with the section's assets listed beneath it as `EXCHANGE:SYMBOL` lines

#### Scenario: Summary as a watchlist
- **WHEN** `GET /api/v1/summary?format=WATCHLIST` is requested
- **THEN** the response is `text/plain` with `###` section headers for the report's asset groups and `EXCHANGE:SYMBOL` lines beneath each

### Requirement: Watchlist output is complete and unannotated
The watchlist output SHALL include every asset present in the report's sections, with no cap applied by the API. It SHALL contain only section headers and symbol lines — no per-asset annotations (flip recency, volume, percentage change) — because the TradingView watchlist format supports only those two line types.

#### Scenario: All assets included
- **WHEN** a report section contains more assets than a TradingView tier would allow
- **THEN** the watchlist output still lists all of them; the API does not truncate

#### Scenario: Only symbols and headers emitted
- **WHEN** the watchlist output is generated for a section whose Markdown shows per-asset detail
- **THEN** the watchlist contains only the `###` header and the `EXCHANGE:SYMBOL` lines, with none of that per-asset detail

### Requirement: Empty sections are omitted
Sections with no assets SHALL be omitted from the watchlist output rather than emitting an empty header.

#### Scenario: Section with no assets
- **WHEN** a report section has no matching assets
- **THEN** no `###` header for that section appears in the watchlist output
