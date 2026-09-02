-- Must be its own migration, same one-transaction-per-enum-addition constraint as V23.
-- MEXC represents MEXC's own single spot market, covering both crypto and tokenized real-equity
-- pairs through the same client and endpoint - unlike Binance's spot/futures split, and distinct
-- from EXCHANGE (real equities via Tiingo).
ALTER TYPE asset_venue ADD VALUE 'MEXC';
