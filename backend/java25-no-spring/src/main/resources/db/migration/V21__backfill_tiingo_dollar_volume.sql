-- TiingoMarketDataProvider previously stored candle.volume as Tiingo's raw share count, while
-- Binance's candle.volume is quote-asset (USDT) dollar volume - two incompatible units mixed
-- together in v_asset_liquidity's AVG(candle.volume), which silently made cross-provider
-- liquidity ranking (ScanSort.LIQUIDITY_DESC, used by both weekly briefing endpoints) meaningless
-- whenever a Tiingo asset was compared against a Binance one. The ingestion code now stores
-- dollar-notional volume (adjVolume * adjClose) going forward; this one-time backfill corrects
-- every already-ingested TIINGO-provider candle to the same semantics, using its own stored
-- (already split/dividend-adjusted) close price.
UPDATE candle c
SET volume = c.volume * c.close
FROM asset a
WHERE c.asset_id = a.id AND a.provider = 'TIINGO';
