## ADDED Requirements

### Requirement: Confluence endpoint returns cross-timeframe aligned assets
The system SHALL expose `GET /api/v1/summary/confluence` which returns assets that are simultaneously bullish on W1 and D1 SuperTrend (bull-aligned), and assets simultaneously bearish on both (bear-aligned). Each list SHALL be ordered by the D1 signal flip date descending (most recent confluence first). Assets with no recorded D1 flip date SHALL appear at the end of their respective list.

#### Scenario: Bull-aligned list returned
- **WHEN** assets A and B are bullish on both W1 and D1 SuperTrend, and A's D1 flip is more recent
- **THEN** the response `bullishConfluence` list contains A first, then B

#### Scenario: Bear-aligned list returned
- **WHEN** assets C and D are bearish on both W1 and D1 SuperTrend
- **THEN** the response `bearishConfluence` list contains both, ordered by D1 flip date descending

#### Scenario: Asset aligned on only one timeframe excluded
- **WHEN** an asset is bullish on D1 but bearish on W1
- **THEN** it does not appear in either confluence list

#### Scenario: Empty lists when no confluence exists
- **WHEN** no assets are aligned on both timeframes in either direction
- **THEN** both `bullishConfluence` and `bearishConfluence` are empty lists

### Requirement: Confluence response includes data freshness metadata
The response SHALL include `lastIngestionAt` (timestamp of last successful pipeline run) and `candlesThrough` (date of most recent daily candle), identical to the existing summary endpoint.

#### Scenario: Freshness fields present
- **WHEN** the endpoint is called after a successful pipeline run
- **THEN** `lastIngestionAt` and `candlesThrough` are non-null in the response

#### Scenario: Freshness fields null before any ingestion
- **WHEN** no ingestion has run yet
- **THEN** `lastIngestionAt` and `candlesThrough` are null

### Requirement: Confluence endpoint supports Markdown format
The system SHALL return a Markdown-formatted report when `format=MARKDOWN` is passed as a query parameter. The Markdown report SHALL include a data freshness header, a bull-aligned section, and a bear-aligned section, each listing assets with their D1 flip recency.

#### Scenario: Markdown format requested
- **WHEN** `GET /api/v1/summary/confluence?format=MARKDOWN` is called
- **THEN** the response content-type is `text/markdown` and the body is a human-readable Markdown brief

#### Scenario: JSON format is the default
- **WHEN** `GET /api/v1/summary/confluence` is called without a `format` parameter
- **THEN** the response is JSON
