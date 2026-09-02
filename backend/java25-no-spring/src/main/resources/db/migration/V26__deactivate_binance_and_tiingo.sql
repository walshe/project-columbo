-- MEXC (V25, run just before this one) now covers every asset class this system tracks -
-- deactivating Binance and Tiingo here, not deleting them, per migrate-to-mexc-provider's
-- "disconnect for now" framing: fully reversible via a single UPDATE, and preserves historical
-- candle/indicator/signal data for these assets rather than orphaning it. Same deactivate-not-
-- delete convention as V14/V22.
--
-- Sequenced after V25's seed insert (not combined into one migration) so a partial/failed
-- deploy can never leave an asset class with zero active assets mid-migration.
UPDATE asset SET active = false WHERE provider IN ('BINANCE', 'TIINGO');
