-- Must be its own migration, same one-transaction-per-enum-addition constraint as V15.
-- EXCHANGE represents a real securities exchange (via Tiingo) where Binance's spot/futures
-- split doesn't apply.
ALTER TYPE asset_venue ADD VALUE 'EXCHANGE';
