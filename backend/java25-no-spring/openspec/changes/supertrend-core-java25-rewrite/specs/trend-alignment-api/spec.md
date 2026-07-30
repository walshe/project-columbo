## ADDED Requirements

### Requirement: Cross-timeframe confluence lists
`GET /api/v1/summary/trend-alignment` SHALL return assets that are simultaneously bullish on both W1 and D1 SuperTrend (bull-aligned) and assets simultaneously bearish on both (bear-aligned), each list ordered by D1 flip date descending, with assets having no recorded D1 flip appearing last.

#### Scenario: Bull-aligned list ordering
- **WHEN** assets A and B are bullish on both W1 and D1, and A's D1 flip is more recent than B's
- **THEN** the bullish-confluence list contains A before B

#### Scenario: Partial alignment excluded
- **WHEN** an asset is bullish on D1 but bearish on W1 (or vice versa)
- **THEN** it appears in neither confluence list

### Requirement: Retest lists
The response SHALL additionally include assets currently in bullish retest and assets currently in bearish retest, within a configurable maximum retest-age window (default 7 days).

#### Scenario: Retest within age window included
- **WHEN** an asset entered retest fewer days ago than `maxRetestAgeDays`
- **THEN** it appears in the corresponding retest list

#### Scenario: Retest older than age window excluded
- **WHEN** an asset entered retest more days ago than `maxRetestAgeDays`
- **THEN** it does not appear in the retest list

### Requirement: Multiple output formats
The endpoint SHALL support `format=JSON` (default), `format=MARKDOWN` (a human-readable report with a freshness header and sections for bull confluence, bull retest, bear confluence, bear retest), and `format=WATCHLIST` (a TradingView-importable plain-text list grouped by section).

#### Scenario: JSON is the default format
- **WHEN** `GET /api/v1/summary/trend-alignment` is called with no `format` parameter
- **THEN** the response is JSON

#### Scenario: Markdown format requested
- **WHEN** `GET /api/v1/summary/trend-alignment?format=MARKDOWN` is called
- **THEN** the response content-type is `text/markdown` and the body includes all four sections

#### Scenario: Watchlist format requested
- **WHEN** `GET /api/v1/summary/trend-alignment?format=WATCHLIST` is called
- **THEN** the response body is plain text with `###SectionHeader` lines followed by `EXCHANGE:SYMBOL` lines importable into TradingView

### Requirement: Freshness metadata included
The response SHALL include the last successful ingestion timestamp and the latest candle date, and SHALL honor an optional `requireFresh` flag with the same staleness-rejection behavior as other read endpoints.

#### Scenario: Freshness fields present
- **WHEN** the endpoint is called after a successful pipeline run
- **THEN** the response includes non-null `lastIngestionAt` and `candlesThrough` fields
