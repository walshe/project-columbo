## Context

`CandleIngestionService` fetches D1 candles per asset through a `MarketDataProvider` (`[startTimeMs, endTimeMs)` contract) and resumes from a previous bar in two places:

- `ingestForAsset` (`CandleIngestionService.java:95`): `startTimeMs = findLatestCloseTime(D1) + 1`.
- `fetchAndUpsertUntilCaughtUp` (`:159`): `nextWindowStart = lastFetched.closeTime + 1`.

The codebase assumes everywhere that `closeTime = openTime + span - 1ms`. Binance klines satisfy that natively, Tiingo bars are normalized to it (`TiingoMarketDataProvider.java:157`), and `Timeframe.openTimeFor` documents it as an exact inverse. `MexcMarketDataProvider.toCandle` stores MEXC's raw `row[6]`, which is `openTime + 86,400,000` (verified live 2026-10-09). MEXC returns only bars with `open >= startTime` (verified: `startTime = 2025-11-13T00:00:00.001Z` returns 11-14 first), so `closeTime + 1` skips the next bar.

Other consequences of the MEXC convention, found by code inspection:
- **Delayed weekly rollup.** `CandleRollupService` only rolls up a week once its last D1 bar's `closeTime` is before today's UTC midnight. A MEXC Sunday bar closes exactly at Monday 00:00, so every new week is held back a day.
- **Flip times 1 ms off.** `SignalQueryService` reports `lastFlipTime` via `openTimeFor(closeTime)`, which comes out 1 ms after the true open for MEXC bars.

Gaps never fill themselves. Ingestion only moves forward from the latest stored bar. `IndicatorComputationService` recomputes incrementally from the last persisted SuperTrend close time. The rollup skips weeks older than the latest stored W1 bar. So a code fix alone leaves existing gaps, and the flips derived from them, in place.

## Goals / Non-Goals

**Goals:**
- No skipped daily bars, for any provider, on both backfill pages and incremental runs.
- One close-time convention for every stored candle.
- Gaps become visible in logs if they happen again for any reason.
- Existing databases (production, Monty local) end up gap-free with correct SuperTrend and flips.

**Non-Goals:**
- A general self-healing gap backfiller (e.g. a periodic "find and refetch missing days" job). The warning makes recurrence visible; revisit if gaps appear for other reasons such as provider outages.
- Changing Binance/Tiingo behaviour (both deactivated; their stored rows already follow the convention).
- Repairing `market_breadth_snapshot` history. It's per-run history that can't be recomputed retroactively, and slightly wrong past snapshots are acceptable.

## Decisions

### D1. Normalize MEXC close time at the provider boundary (handoff option B)
`MexcMarketDataProvider.toCandle` sets `closeTime = openTime + 1 day - 1ms` (ignoring `row[6]` beyond a sanity check). This restores the system-wide invariant, so the rollup's finalized check, `openTimeFor` and freshness all become correct without touching them. It's the same fix already applied for Tiingo.
*Alternative (handoff option A only):* fixing just the two resume points stops the gaps, but leaves the Monday rollup delay and 1 ms flip-time offset, and keeps a hidden per-provider convention that the next provider could trip over again.

### D2. Also resume from open time (handoff option A, defense in depth)
Both resume points advance from the last bar's **open** time + 1 ms:
- `fetchAndUpsertUntilCaughtUp`: `nextWindowStart = last.openTime + 1`.
- `ingestForAsset`: `startTimeMs = findLatestOpenTime(D1) + 1` (new `CandleDao.findLatestOpenTime`, same shape as `findLatestCloseTime`).

Any provider that returns bars with `open >= startTime` then gets the next bar (it opens a full day later), whatever its close-time convention. With D1 alone the old `+1` would also work, but D2 means correctness no longer depends on every future provider normalizing. Binance/MEXC re-request nothing extra: the last stored bar's open is `< startTime`.
*Alternative:* `startTime = lastClose` (no +1). That re-fetches the boundary bar on Binance-style providers and relies on an idempotent upsert. It's correct but wasteful and still convention-dependent.

### D3. Warn on gaps
In `fetchAndUpsertUntilCaughtUp`, compare each fetched bar's open with the previous bar's open: the latest stored bar for the first bar of a run, the prior fetched bar otherwise. Log `WARN` with symbol and range when the difference exceeds 1 day. Logging only, no failure: real gaps can occur (exchange halts, listings), and failing the asset would block all later data.

### D4. Repair via a Flyway migration that resets MEXC market data
`V27__reset_mexc_market_data.sql` deletes `signal_state`, `indicator_supertrend` and `candle` rows (both timeframes) for every asset with `provider = 'MEXC'`, in dependency order, inside the migration's transaction. On the next ingestion run each MEXC asset has no stored candles, so it backfills from `SUPERTREND_BACKFILL_START` with the fixed code. The rest of the pipeline (indicators, signals, rollup, W1 indicators/signals, pulse) runs from scratch as for a new asset.
- Why a migration: it runs automatically and exactly once in every environment (production and Monty's local DB), with no manual SQL step to forget. Everything deleted can be re-derived from MEXC's public API.
- Why delete instead of `UPDATE close_time = close_time - 1ms`: an update would fix the convention but not fill the gaps, and the gap days' SuperTrend/signals would still need recomputing from the gap onward. Re-backfilling fixes all of it at once.
- Inactive Binance/Tiingo rows are untouched. They already follow the convention and aren't served (active-only reads).
- The SQL must be checked against a clone of a real Columbo DB before merge (row counts, FK order, transaction). An attempt on 2026-10-09 was blocked by a full Docker disk, so this is a task.
*Alternative:* an admin endpoint (`POST /internal/ingestion/rebuild`). That would be reusable, but it needs manual triggering per environment and adds a destructive API. It's not justified for a one-off repair.

### D6. MEXC provider skips empty 500-day windows
Found during end-to-end verification of D5: MEXC only returns bars in `[startTime, startTime + 500 days)`. An explicit `limit=1000` doesn't widen it, and a start ≥ 500 days before listing returns `[]` (probed live on `AMATONUSDT`, listed 2026-01-15). `CandleIngestionService` stops paging on an empty page, so with a 2024-07-01 start, 24 of 96 assets listed after ~2025-11 never got data. Under the old 2026-01-01 start this was masked. The fix lives in `MexcMarketDataProvider.fetchDailyCandles`: while a window comes back empty and `windowStart + 500d < endTime`, advance `windowStart` by 500 days and retry. That restores the provider's `[start, end)` contract, so the generic ingestion loop (and its "empty page = nothing more for now" rule) stays unchanged. Cost: at most `ceil(range / 500d)` extra calls per never-backfilled asset, one in practice.
*Alternative:* teaching `CandleIngestionService` to keep paging past empty pages. Rejected because that's provider-specific knowledge in generic code, and Binance-style providers would make pointless extra calls.

### D5. Backfill horizon 2024-07-01
Update `compose.yaml` and the README examples to `SUPERTREND_BACKFILL_START=2024-07-01T00:00:00Z`. Monty measured this (BTC + a 30-asset sample) as the latest start that reproduces full-history SuperTrend flips. It also means the repair backfill spans two MEXC pages, which exercises the paging fix in production. The production value is set wherever the deploy env is configured. The operator must confirm it there (task).

## Risks / Trade-offs

- [Data unavailable between deploy and the first completed run] → Trigger a run immediately after deploy (`POST /api/v1/internal/ingestion/run`). The backfill takes minutes (about 96 assets × 2 pages with polite delay). Deploy outside the scheduled run and briefing times.
- [Destructive migration, no rollback of data] → Data is fully re-derivable from MEXC. Rolling back the code after V27 would re-ingest with the old bug, but wouldn't lose anything not re-fetchable.
- [MEXC history shorter than 2024-07-01 for newer listings] → Those assets start at their listing, the same as any new asset.
- [Changed stored close times shift `candlesThrough` by -1 ms in API responses (`…T23:59:59.999Z`)] → Same format Binance-era responses had; consumers treat it as a timestamp.
- [`row[6]` semantics change again on MEXC's side] → D1 ignores it; D2 doesn't depend on it.

## Migration Plan

1. Merge and deploy. Flyway applies V27 on startup.
2. Trigger `POST /api/v1/internal/ingestion/run` (or wait for the scheduler) and wait for completion.
3. Verify: no missing D1 days for active assets since the backfill start (query in tasks), BTCUSDT W1 has 2025-11-10 with `BEARISH_REVERSAL`, and `BULLISH_REVERSAL` on 2026-08-31.
4. Rollback: revert the code. Data stays in the new convention, which the old code reads fine; only the resume bug returns.

## Open Questions

- Should the gap warning also feed a metric or appear in the ingestion run summary? Deferred: logging first.
