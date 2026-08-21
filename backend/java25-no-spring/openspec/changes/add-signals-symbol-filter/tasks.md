## 1. Symbol filter on `/api/v1/signals`

- [x] 1.1 Add an optional `symbols` (`Set<String>`) parameter to `SignalQueryService.listSignals`, applied as an in-memory filter in `summarize` alongside the existing `stateFilter`
- [x] 1.2 Add test coverage for `summarize`'s filtering: single symbol, multiple symbols, unknown symbol (silently empty result, not an error), combined with `state`/`assetClass`
- [x] 1.3 Parse a comma-separated `symbols` query param in `SignalsHandler.getSignals` into a `Set<String>` (`null` when absent), pass through to `SignalQueryService.listSignals`
- [x] 1.4 Extend the `@OpenApi` annotation on `GET /api/v1/signals` with the new `symbols` param: format (comma-separated), exact-case-match requirement, silent-empty-on-unknown-symbol behavior, one example value

## 2. `@OpenApi` documentation audit (all endpoints)

Every route already has an `@OpenApi` annotation, but several are thin relative to how a tool-calling AI agent actually needs to use them - request/response are referenced by class with no field-level description, and some param descriptions are a few words with no example value or edge-case note. Raise every endpoint to the same bar: each `@OpenApiParam` states what the value means, its exact format/constraints, and default behavior when omitted; each request/response `@OpenApiContent` class gets field-level `@OpenApiExample`/description coverage where the field name alone doesn't make the meaning and units obvious (e.g. is a percentage a fraction or already `x100`, is a timestamp UTC, what does `null` mean for a given field).

- [x] 2.1 `SignalsHandler` (`GET /api/v1/signals`, `GET /api/v1/assets/by-state`) - document `SignalSummary`/`SignalListResponse` fields (what `lastFlipTime` being null means, `pctChangeSinceFlip` sign/precision/null case, `avgVolume7d` units) - these records' Javadoc was already thorough; added top-level `description` and per-param descriptions to both `@OpenApi` blocks instead
- [x] 2.2 `ScanHandler` (`POST /api/v1/scan`) - documented `ScanRequest`/`ScanCondition`/`ScanResult`/`ScanResponse` fields, the AND/OR operator semantics (with a worked example), what a cross-timeframe condition set means, and `maxDaysSinceFlip`/`limit`/`sort` defaults when omitted; added missing `operator`/`conditions` Javadoc to `ScanRequest` itself
- [x] 2.3 `TrendAlignmentHandler` (`GET /api/v1/summary/trend-alignment`) - documented confluence vs. retest terminology (added to `TrendAlignmentResponse`'s Javadoc directly, since that's the actual domain definition, plus the `@OpenApi` description) and every `format`/`maxRetestAgeDays`/`assetClass` param
- [x] 2.4 `SummaryHandler` - documented the summary response shape (SuperTrend-only, no RSI) and the `format`/`timeframe`/`assetClass` params; `SummaryResponse`'s own Javadoc was already thorough
- [x] 2.5 `WeeklyTrendBriefingHandler`/`WeeklyPullbackBriefingHandler` - documented that these return `text/markdown` (not JSON), that the call is synchronous and blocks on a full ingestion pipeline run (callers need a generous timeout), and what each report's distinguishing section (Flips Forming / watch: annotations) means
- [x] 2.6 `CandleCoverageHandler` - spot-checked; `CandleCoverage`'s Javadoc and the existing `@OpenApi` block were already clear, no change needed
- [x] 2.7 `IngestionTriggerHandler` - documented the async nature (202 = started, not finished), what 409 means, and - since no completion-polling endpoint exists - pointed callers at `/api/v1/candles/coverage` or any read endpoint's freshness metadata instead
- [x] 2.8 Spot-checked the generated OpenAPI JSON (`target/classes/openapi-plugin/openapi-default.json`, produced by the `openapi-annotation-processor` at compile time) after the pass - confirmed the richer `/api/v1/signals` descriptions render correctly, not just compile; also covered by the existing `OpenApiDocumentationIntegrationTest`

## 3. Verification

- [x] 3.1 `mvn compile` clean
- [x] 3.2 `mvn test` - full suite green including new tests
- [x] 3.3 Ran the app locally (`docker compose up postgres` + `mvn exec:java` against real Binance backfill); verified live: `symbols=BTCUSDT` returns exactly one row, `symbols=BTCUSDT,ETHUSDT` returns two, `symbols=NOTASYMBOL` returns an empty array (200, not an error), and `symbols=BTCUSDT,ETHUSDT&state=BULLISH` / `&state=BEARISH` compose correctly (2 and 0 results respectively, matching both assets' actual live trend state)

## 4. Documentation

- [x] 4.1 Updated `backend/java25-no-spring/README.md`'s signals endpoint documentation to mention the new `symbols` filter
