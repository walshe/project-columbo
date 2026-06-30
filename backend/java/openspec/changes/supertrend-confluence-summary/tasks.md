## 1. DTO

- [x] 1.1 Create `ConfluenceSummaryReport` record with fields: `bullishConfluence`, `bearishConfluence` (both `List<SignalStateDto>`), `lastIngestionAt`, `candlesThrough`

## 2. Service

- [x] 2.1 Create `ConfluenceSummaryService` — fetch W1 bullish and D1 bullish signal lists via `SignalQueryService`, intersect by asset symbol, sort by D1 `lastFlipAt` descending
- [x] 2.2 Add bear-aligned logic — same intersection for W1 bearish + D1 bearish
- [x] 2.3 Include `lastIngestionAt` and `candlesThrough` from `IngestionStatusService`

## 3. Controller

- [x] 3.1 Create `ConfluenceSummaryController` at `GET /api/v1/summary/confluence` with `format` param (JSON default, MARKDOWN)
- [x] 3.2 Wire Markdown rendering via `SummaryReportFormatter` — add `formatConfluenceMarkdown(ConfluenceSummaryReport)` method

## 4. Markdown Formatter

- [x] 4.1 Add `formatConfluenceMarkdown` to `SummaryReportFormatter` — data freshness header, bull-aligned section, bear-aligned section, each listing symbol + days since D1 flip + volume

## 5. Tests

- [x] 5.1 Unit test `ConfluenceSummaryService`: asset on both timeframes included, asset on only one timeframe excluded, ordering by D1 flip date
- [x] 5.2 Unit test `SummaryReportFormatter.formatConfluenceMarkdown`: non-empty and empty list cases
- [x] 5.3 Integration test `ConfluenceSummaryController`: JSON and MARKDOWN response formats, 200 status
