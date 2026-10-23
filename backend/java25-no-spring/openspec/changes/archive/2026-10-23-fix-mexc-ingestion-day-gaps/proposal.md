## Why

Since the MEXC migration (PR #76), D1 ingestion silently skips a daily bar for every asset whenever it resumes from a previously fetched bar: between backfill pages and at the start of each incremental run. MEXC reports a kline's close time as the **next bar's open time** (`close - open = 86,400,000 ms`), whereas Binance and our normalized Tiingo bars use `open + span - 1ms`. Ingestion resumes at `closeTime + 1ms`, and MEXC only returns bars whose open time is `>= startTime`, so the next bar is never requested.

The missing days leave incomplete weeks, which the rollup drops, and that shifts SuperTrend flips by a bar. For example, BTCUSDT's weekly bearish flip is reported on 2025-11-17 instead of 2025-11-10, which disagrees with TradingView. Wrong flips flow into `/signals`, scans, weekly briefings and Monty's new charts. Reported and reproduced in Monty's `COLUMBO_INGESTION_GAP_HANDOFF.md` (2026-10-09). We re-verified it against the code and MEXC's live API.

## What Changes

- **Normalize MEXC close times** in `MexcMarketDataProvider` to the system-wide convention `closeTime = openTime + 1 day - 1ms`, as `TiingoMarketDataProvider` already does, so every stored candle satisfies the invariant that `Timeframe.openTimeFor`, the W1 rollup and freshness checks assume.
- **Resume ingestion from the last bar's open time**, not its close time, at both resume points (backfill paging and the start of an incremental run). This makes resuming independent of any provider's close-time convention.
- **Skip empty MEXC windows before a listing.** MEXC answers only `[startTime, startTime + 500 days)`, so a backfill start more than 500 days before a symbol's listing returns an empty page, and ingestion stopped there. With the 2024-07-01 start this left 24 newer listings (mostly tokenized stocks) with **no data at all**, found in end-to-end verification. `MexcMarketDataProvider` now steps forward through empty 500-day windows until bars appear or `endTime` is reached.
- **Detect gaps at ingestion time**: log a warning when consecutive fetched bars, or the stored latest bar and the first fetched bar, are more than one day apart. Today a gap goes completely unnoticed.
- **Repair existing data** with a Flyway migration that deletes all MEXC assets' candles (D1+W1), SuperTrend rows and signal-state rows. The next ingestion run re-backfills them from `SUPERTREND_BACKFILL_START` with the fixed code. **BREAKING (data)**: until that run completes, MEXC assets have no data.
- **Raise the backfill horizon** in `compose.yaml`/README examples from `2026-01-01` to `2024-07-01`, the latest start Monty found that reproduces full-history SuperTrend flips (with 2026-01-01, BTC's late-2025 weekly bearish flip is missed entirely).

## Capabilities

### New Capabilities
- `daily-candle-ingestion-continuity`: Ingestion fetches every daily bar without gaps regardless of provider close-time convention, stores candles under a single close-time convention, flags gaps, and repairs data ingested under the broken behaviour.

### Modified Capabilities
<!-- None: openspec/specs/ has no baseline specs in this instance; existing behaviour lives in archived/active change specs. -->

## Impact

- **Code**: `ingestion/MexcMarketDataProvider.java` (close-time normalization), `ingestion/CandleIngestionService.java` (resume-from-open, gap warnings), `persistence/CandleDao.java` (latest open-time lookup).
- **Database**: new migration `V27__reset_mexc_market_data.sql` (deletes MEXC `candle`, `indicator_supertrend`, `signal_state` rows; no schema change). `market_breadth_snapshot` history is kept.
- **Operations**: after deploy, the first ingestion run re-backfills about 96 assets × 2 MEXC pages. Until it finishes, MEXC-backed endpoints return no data or stale data. `candlesThrough`/stored close times for MEXC change from `…T00:00:00Z` to `…T23:59:59.999Z` of the bar's own day.
- **Consumers**: flip dates for affected weeks move to their correct bar. Monty needs no change. Its local Columbo database is repaired by the same migration.
