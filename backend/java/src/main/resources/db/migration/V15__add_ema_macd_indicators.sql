-- V15__add_ema_macd_indicators.sql
-- Foundation indicator tables for Phase 5 (EMA + MACD).
-- No enum changes needed: EMA and MACD feed into Elder Impulse state (Phase 6).

CREATE TABLE indicator_ema (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL REFERENCES asset(id),
    timeframe timeframe NOT NULL,
    period INTEGER NOT NULL,
    close_time TIMESTAMPTZ NOT NULL,
    ema_value NUMERIC(20, 8) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT unique_ema_asset_timeframe_period_close UNIQUE (asset_id, timeframe, period, close_time)
);

CREATE INDEX idx_ema_asset_timeframe_period_close ON indicator_ema (asset_id, timeframe, period, close_time DESC);

CREATE TABLE indicator_macd (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL REFERENCES asset(id),
    timeframe timeframe NOT NULL,
    close_time TIMESTAMPTZ NOT NULL,
    macd_line NUMERIC(20, 8) NOT NULL,
    signal_line NUMERIC(20, 8) NOT NULL,
    histogram NUMERIC(20, 8) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT unique_macd_asset_timeframe_close UNIQUE (asset_id, timeframe, close_time)
);

CREATE INDEX idx_macd_asset_timeframe_close ON indicator_macd (asset_id, timeframe, close_time DESC);
