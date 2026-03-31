-- V9__add_indicator_rsi.sql

-- 1. Extend ENUMs
-- PostgreSQL doesn't support adding values to ENUMs inside a transaction in older versions, 
-- but Flyway runs in a transaction by default.
-- However, standard practice in this project (see V8) is to just use ALTER TYPE.

ALTER TYPE indicator_type ADD VALUE 'RSI';
ALTER TYPE trend_state ADD VALUE 'ABOVE_60';
ALTER TYPE trend_state ADD VALUE 'BELOW_40';
ALTER TYPE trend_state ADD VALUE 'NEUTRAL';
ALTER TYPE signal_event ADD VALUE 'CROSSED_ABOVE_60';
ALTER TYPE signal_event ADD VALUE 'CROSSED_BELOW_40';

-- 2. Create indicator_rsi table
CREATE TABLE indicator_rsi (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL REFERENCES asset(id),
    timeframe timeframe NOT NULL,
    close_time TIMESTAMPTZ NOT NULL,
    rsi_value NUMERIC(10,4) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT unique_rsi_asset_timeframe_close UNIQUE (asset_id, timeframe, close_time)
);

-- 3. Add index for faster lookups of latest values
CREATE INDEX idx_rsi_asset_timeframe_close_time ON indicator_rsi (asset_id, timeframe, close_time DESC);
