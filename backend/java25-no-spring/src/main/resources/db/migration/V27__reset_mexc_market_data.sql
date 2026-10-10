-- Rebuilds every MEXC asset's market data from scratch (fix-mexc-ingestion-day-gaps).
--
-- Before this fix, MEXC candles were stored with MEXC's raw close time (= the next bar's open),
-- and ingestion resumed at closeTime + 1ms, which MEXC answers by skipping the next bar. That left
-- missing D1 days (between backfill pages and after incremental runs), missing W1 weeks (the rollup
-- drops incomplete weeks) and SuperTrend flips shifted onto the wrong bar.
--
-- Ingestion, indicator computation and the W1 rollup only ever move forward from the latest stored
-- row, so fixing the code alone would never fill those gaps. Deleting the rows makes the next
-- ingestion run treat each MEXC asset as new: backfill from SUPERTREND_BACKFILL_START with
-- normalized close times (open + 1 day - 1ms), then recompute SuperTrend, signals and W1 rollups.
-- Everything deleted is re-fetchable from MEXC's public API.
--
-- Scope: MEXC assets only (active or not); deactivated BINANCE/TIINGO rows already follow the
-- close-time convention and are kept. market_breadth_snapshot is per-run history that can't be
-- recomputed retroactively, so it's kept too.
DELETE FROM signal_state WHERE asset_id IN (SELECT id FROM asset WHERE provider = 'MEXC');
DELETE FROM indicator_supertrend WHERE asset_id IN (SELECT id FROM asset WHERE provider = 'MEXC');
DELETE FROM candle WHERE asset_id IN (SELECT id FROM asset WHERE provider = 'MEXC');
