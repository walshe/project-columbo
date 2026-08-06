-- Every asset previously hit Binance spot (api.binance.com) regardless of class. Spot and futures
-- (fapi.binance.com) are separate Binance products with separate hosts AND separate klines paths -
-- every STOCK/ETF/COMMODITY asset, plus 4 crypto assets that happen to be futures-only
-- (HYPEUSDT/CCUSDT/MUSDT/KASUSDT, verified directly against both real APIs), only exist on futures
-- and were getting "-1121 Invalid symbol" from spot, auto-deactivating them.
CREATE TYPE asset_venue AS ENUM ('SPOT', 'FUTURES');

ALTER TABLE asset ADD COLUMN venue asset_venue NOT NULL DEFAULT 'SPOT';

UPDATE asset SET venue = 'FUTURES' WHERE asset_class IN ('STOCK', 'ETF', 'COMMODITY');

UPDATE asset SET venue = 'FUTURES'
WHERE provider = 'BINANCE' AND symbol IN ('HYPEUSDT', 'CCUSDT', 'MUSDT', 'KASUSDT');

-- Self-heal a deployment that already ran ingestion and got these auto-deactivated for exactly
-- this reason - not just fresh databases.
UPDATE asset SET active = true
WHERE active = false
  AND (
    asset_class IN ('STOCK', 'ETF', 'COMMODITY')
    OR (provider = 'BINANCE' AND symbol IN ('HYPEUSDT', 'CCUSDT', 'MUSDT', 'KASUSDT'))
  );
