-- V11__append_usdt_to_asset_symbols.sql
-- Appends 'USDT' to all asset symbols that don't already have it

UPDATE asset
SET symbol = symbol || 'USDT'
WHERE symbol NOT LIKE '%USDT';
