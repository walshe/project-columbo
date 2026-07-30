## ADDED Requirements

### Requirement: Trend alignment report includes retest lists
The system SHALL include `bullishRetest` and `bearishRetest` lists in the trend alignment response. A bullish retest asset is one currently bullish on W1 SuperTrend whose D1 SuperTrend flipped bearish within the last `maxRetestAgeDays` days. A bearish retest asset is one currently bearish on W1 whose D1 flipped bullish within the last `maxRetestAgeDays` days. Each list SHALL be ordered by D1 flip date descending.

#### Scenario: Asset in bullish retest included
- **WHEN** an asset is W1 bullish and its D1 flipped bearish 3 days ago, and `maxRetestAgeDays` is 7
- **THEN** the asset appears in `bullishRetest` and does NOT appear in `bullishConfluence`

#### Scenario: Asset excluded from retest when D1 counter-trend too long
- **WHEN** an asset is W1 bullish and its D1 flipped bearish 10 days ago, and `maxRetestAgeDays` is 7
- **THEN** the asset does NOT appear in `bullishRetest`

#### Scenario: Asset in bearish retest included
- **WHEN** an asset is W1 bearish and its D1 flipped bullish 2 days ago, and `maxRetestAgeDays` is 7
- **THEN** the asset appears in `bearishRetest` and does NOT appear in `bearishConfluence`

#### Scenario: Empty retest lists when no retests exist
- **WHEN** no assets meet the retest criteria
- **THEN** both `bullishRetest` and `bearishRetest` are empty lists

### Requirement: Retest window is configurable per request
The endpoint SHALL accept an optional `maxRetestAgeDays` integer query parameter. When omitted, it SHALL default to 7. The value controls the maximum number of days since D1 flipped counter-trend for an asset to qualify as a retest.

#### Scenario: Default retest window applied
- **WHEN** `maxRetestAgeDays` is not provided
- **THEN** the retest window defaults to 7 days

#### Scenario: Custom retest window applied
- **WHEN** `maxRetestAgeDays=3` is provided
- **THEN** only assets whose D1 flipped counter-trend within the last 3 days appear in retest lists

### Requirement: Markdown format renders all four sections
The Markdown report SHALL include four sections: W1+D1 Bullish Confluence, W1+D1 Bullish Retest, W1+D1 Bearish Confluence, W1+D1 Bearish Retest. Each section SHALL list assets with their D1 flip recency and volume, or a "None" message if empty.

#### Scenario: All four sections present in Markdown
- **WHEN** `format=MARKDOWN` is requested
- **THEN** the response body contains headings for all four sections

#### Scenario: Retest section shows empty message when no retests
- **WHEN** no assets qualify as retests and `format=MARKDOWN` is requested
- **THEN** the retest sections display "None — no retest setups found."
