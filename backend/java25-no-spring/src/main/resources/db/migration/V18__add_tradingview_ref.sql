-- Nullable "EXCHANGE:SYMBOL" TradingView reference (e.g. 'NASDAQ:AAPL', 'OTC:SSNLF', 'SSE:601398'),
-- populated for real-equity (EXCHANGE-venue) assets where the correct TradingView listing has been
-- verified. Not derivable from provider/symbol alone - Tiingo's own ticker doesn't always match
-- TradingView's symbol format (e.g. Tiingo's 'BRK-A' is TradingView's 'BRK.A'), and the exchange
-- (NASDAQ/NYSE/OTC/SSE/...) isn't stored anywhere else on this table.
ALTER TABLE asset ADD COLUMN tradingview_ref VARCHAR;
