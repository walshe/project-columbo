-- 00004_create_indicator_supertrend.sql

CREATE TABLE indicator_supertrend (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL REFERENCES asset(id),
    timeframe timeframe NOT NULL,
    close_time TIMESTAMPTZ NOT NULL,
    atr NUMERIC NOT NULL,
    upper_band NUMERIC NOT NULL,
    lower_band NUMERIC NOT NULL,
    supertrend NUMERIC NOT NULL,
    direction supertrend_direction NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT unique_supertrend_asset_timeframe_close UNIQUE (asset_id, timeframe, close_time)
);
