## 1. DTO

- [x] 1.1 Add `bullishRetest` and `bearishRetest` fields (`List<SignalStateDto>`) to `ConfluenceSummaryReport`

## 2. Service

- [x] 2.1 Add retest intersection logic to `ConfluenceSummaryService` — fetch W1 bullish + D1 bearish signals, intersect by symbol, filter to assets whose D1 `daysSinceFlip <= maxRetestAgeDays`
- [x] 2.2 Add bearish retest logic — same pattern: W1 bearish + D1 bullish, filter by `maxRetestAgeDays`
- [x] 2.3 Thread `maxRetestAgeDays` parameter through `getConfluence(int maxRetestAgeDays)`

## 3. Controller

- [x] 3.1 Rename controller path from `/api/v1/summary/confluence` to `/api/v1/summary/trend-alignment`
- [x] 3.2 Add `maxRetestAgeDays` query param (default 7) and pass to service

## 4. Markdown Formatter

- [x] 4.1 Add bull retest and bear retest sections to `formatConfluenceMarkdown` in `SummaryReportFormatter`
- [x] 4.2 Use "None — no retest setups found." as the empty message for retest sections

## 5. Tests

- [x] 5.1 Update `ConfluenceSummaryServiceTest` — add tests for retest inclusion, exclusion beyond window, and empty retest case
- [x] 5.2 Update `ConfluenceSummaryFormatterTest` — add tests for retest sections in Markdown output
- [x] 5.3 Update `ConfluenceSummaryControllerTest` — update path to `/api/v1/summary/trend-alignment`, add test for `maxRetestAgeDays` param, verify old `/confluence` path returns 404
