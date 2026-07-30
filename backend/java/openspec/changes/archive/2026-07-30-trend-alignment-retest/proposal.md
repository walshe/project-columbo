## Why

The existing `/api/v1/summary/confluence` endpoint reports assets currently aligned on both W1 and D1 SuperTrend, but misses a high-value setup: assets where the weekly trend is intact but the daily has briefly pulled counter-trend — a retest before continuation. Surfacing these alongside true confluences gives a more complete picture for trade timing.

## What Changes

- **BREAKING** Rename `/api/v1/summary/confluence` → `/api/v1/summary/trend-alignment`
- Add two "retest" lists to the response (bullish retest, bearish retest): assets where W1 is aligned but D1 flipped counter-trend within the last `maxRetestAgeDays` days
- Add `maxRetestAgeDays` query param (default 7) to control the retest window
- Response grows from 2 sections (bullish confluence, bearish confluence) to 4 sections (bull confluence, bull retest, bear confluence, bear retest)
- Markdown format updated to render all four sections

## Capabilities

### New Capabilities

- `trend-alignment-report`: Four-section trend alignment report (bull confluence, bull retest, bear confluence, bear retest) with configurable retest window

### Modified Capabilities

- `supertrend-confluence`: Endpoint renamed and response schema extended with retest lists and `maxRetestAgeDays` param

## Impact

- `ConfluenceSummaryController` — rename path, add `maxRetestAgeDays` param
- `ConfluenceSummaryService` — add retest intersection logic
- `ConfluenceSummaryReport` DTO — add `bullishRetest` and `bearishRetest` fields
- `SummaryReportFormatter` — render two additional sections in Markdown
- All existing tests updated to new path; new tests for retest logic
- Clients calling `/api/v1/summary/confluence` must update to `/api/v1/summary/trend-alignment`
