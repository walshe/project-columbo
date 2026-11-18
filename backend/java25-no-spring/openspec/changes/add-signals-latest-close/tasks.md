## 1. SignalSummary record

- [x] 1.1 Add `latestClose` (`BigDecimal`, nullable, no `requireNonNull`) as a new parameter to the `SignalSummary` record — place it after `pctChangeSinceFlip` for logical grouping

## 2. Service wiring

- [x] 2.1 Update `SignalQueryService.toSummary` to pass `latestClose` into the `SignalSummary` constructor (the value is already in scope as the `latestClose` parameter)

## 3. Test updates

- [x] 3.1 Update `SignalQueryServiceTest` — add `latestClose` to every direct `SignalSummary` constructor call; add a scenario asserting `latestClose` is populated correctly when a candle exists and null when it does not
- [x] 3.2 Update `SignalQueryServiceIntegrationTest` — add `latestClose` to any direct `SignalSummary` constructions and assert the field value in response assertions
- [x] 3.3 Fix any other test files that construct `SignalSummary` directly (compiler will flag them; check `TrendAlignmentServiceTest`, `ScanServiceTest`, `TrendAlignmentServiceIntegrationTest`)

## 4. API documentation

- [x] 4.1 Update the `@OpenApi` annotation on `SignalsHandler.getSignals` to document `latestClose` on the `SignalSummary` response schema (nullable `BigDecimal`, description: "Most recent candle close price for this asset on the requested timeframe; null if no candles exist")
