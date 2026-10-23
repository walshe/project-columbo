## ADDED Requirements

### Requirement: Single close-time convention for stored candles
Every D1 candle the system stores SHALL have `closeTime = openTime + 1 day - 1 ms`, regardless of the provider's native close-time representation. `MexcMarketDataProvider` SHALL derive the close time from the kline's open time rather than storing MEXC's raw close time.

#### Scenario: MEXC kline with next-open close time
- **WHEN** MEXC returns a 1d kline with open time `2025-11-12T00:00:00Z` and close time `2025-11-13T00:00:00Z`
- **THEN** the parsed candle has open time `2025-11-12T00:00:00Z` and close time `2025-11-12T23:59:59.999Z`

#### Scenario: Open time inverse holds for MEXC candles
- **WHEN** any stored MEXC D1 candle's close time is passed to `Timeframe.D1.openTimeFor`
- **THEN** the result equals that candle's stored open time exactly

### Requirement: Ingestion resumes without skipping bars
When ingestion resumes from a previously fetched or stored bar, the next fetch window SHALL start at that bar's open time + 1 ms, so the next bar (which opens one day later) is included for any provider that returns bars with `open >= startTime`. This SHALL hold both between pages within one run and at the start of a run that resumes from the latest stored D1 bar.

#### Scenario: Backfill spanning a page boundary
- **WHEN** a provider returns history in pages of N bars whose close time equals the next bar's open time, and ingestion backfills a range longer than N bars
- **THEN** every day in the range is stored, including the first bar of each subsequent page

#### Scenario: Incremental run after a stored bar
- **WHEN** the latest stored D1 bar opens at `2026-10-05T00:00:00Z` and a new run starts
- **THEN** the first fetch requests `startTime = 2026-10-05T00:00:00.001Z` and the bar opening `2026-10-06T00:00:00Z` is stored

#### Scenario: Binance-style close times still work
- **WHEN** a provider returns bars with `closeTime = next open - 1 ms` across a page boundary
- **THEN** no day is skipped and no bar is fetched twice within the run

### Requirement: Backfill reaches assets listed long after the backfill start
`MexcMarketDataProvider.fetchDailyCandles` SHALL return the earliest bars in `[startTime, endTime)` even when `startTime` is more than 500 days before the symbol's first bar, by skipping forward through empty 500-day request windows. It SHALL return an empty list only when no bars exist in the whole range.

#### Scenario: Asset listed 600 days after the backfill start
- **WHEN** ingestion backfills a MEXC asset whose first bar is 600 days after `SUPERTREND_BACKFILL_START`
- **THEN** its bars from the listing date onward are stored in the same run

#### Scenario: Range with no bars at all
- **WHEN** the requested range ends before the symbol's listing
- **THEN** the provider returns an empty list after stepping through the range, without looping indefinitely

### Requirement: Gaps are reported
Ingestion SHALL log a warning naming the asset and the missing range whenever two consecutive bars (the latest stored bar and the first fetched bar of a run, or two consecutive fetched bars) have open times more than one day apart. A gap SHALL NOT fail the asset's ingestion.

#### Scenario: Provider returns a page with a missing day
- **WHEN** a fetched page contains bars opening on 2026-10-05 and 2026-10-07 but not 2026-10-06
- **THEN** a WARN log names the asset and the gap, and all returned bars are still stored

### Requirement: Weekly rollup includes a week on the Monday after it ends
With normalized close times, the W1 rollup SHALL treat a complete week as finalized as soon as UTC midnight on the following Monday has passed.

#### Scenario: Rollup on Monday
- **WHEN** the rollup runs at `2026-10-12T00:30Z` (Monday) and all seven D1 bars for the week starting `2026-10-05` are stored
- **THEN** the W1 candle for the week of `2026-10-05` is stored in that run

### Requirement: Existing MEXC data is rebuilt
A database migration SHALL delete all `signal_state`, `indicator_supertrend` and `candle` rows (D1 and W1) belonging to assets with provider `MEXC`, leaving other providers' rows and `market_breadth_snapshot` untouched. The next ingestion run SHALL then backfill those assets from `SUPERTREND_BACKFILL_START` and recompute SuperTrend, signals and W1 rollups from scratch.

#### Scenario: Migration scope
- **WHEN** the migration runs on a database containing MEXC and BINANCE assets' candles, indicators and signal states
- **THEN** no rows remain for MEXC assets in those three tables, and BINANCE assets' rows are unchanged

#### Scenario: Gap-free after rebuild
- **WHEN** the migration has run and one ingestion run has completed with backfill start `2024-07-01`
- **THEN** no calendar day between the backfill start and yesterday is missing from D1 candles for any active MEXC asset that has history for that whole range

#### Scenario: Correct flips after rebuild
- **WHEN** the rebuild has completed
- **THEN** BTCUSDT has a W1 candle opening `2025-11-10`, a W1 `BEARISH_REVERSAL` on the week of `2025-11-10` and a W1 `BULLISH_REVERSAL` on the week of `2026-08-31`
