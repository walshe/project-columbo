## 1. DTO

- [x] 1.1 Add `pctChangeSinceFlip` (`BigDecimal`, nullable) field to `SignalStateDto`

## 2. Repository

- [x] 2.1 Add bulk query to `CandleRepository` — fetch latest close price per asset for a given timeframe, returning `(assetId, closePrice)` pairs
- [x] 2.2 Add bulk query to `CandleRepository` — fetch close prices for a set of `(assetId, closeTime)` pairs (flip-time candle lookup)

## 3. Mapper

- [x] 3.1 Update `SignalStateMapper.toDto` to accept `flipClosePrice` and `latestClosePrice` parameters and compute `pctChangeSinceFlip = ((latest - flip) / flip) × 100`, rounded to 2 d.p., null-safe

## 4. Service

- [x] 4.1 In `SignalQueryService.listSignals`, bulk-fetch latest close prices for all result assets (one query)
- [x] 4.2 Bulk-fetch flip-time close prices for assets that have a recorded flip (one query keyed on `assetId + closeTime`)
- [x] 4.3 Pass both price maps through to `SignalStateMapper.toDto`

## 5. Markdown Formatter

- [x] 5.1 Update `appendSignals` in `SummaryReportFormatter` to include `pctChangeSinceFlip` when non-null (format: `+N.NN%` / `-N.NN%`)
- [x] 5.2 Update `appendConfluenceSignals` and `appendRetestSignals` to include `pctChangeSinceFlip` when non-null

## 6. Tests

- [x] 6.1 Unit test `SignalStateMapper` — positive pct change, negative pct change, null when no flip price, null when no latest price
- [x] 6.2 Update all existing tests that construct `SignalStateDto` directly — add `null` for `pctChangeSinceFlip`
- [x] 6.3 Unit test Markdown formatter — `+N.NN%` and `-N.NN%` rendered, null case omitted
