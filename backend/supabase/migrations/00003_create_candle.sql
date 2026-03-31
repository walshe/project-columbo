-- 00003_create_candle.sql

CREATE TABLE candle (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL REFERENCES asset(id),
    timeframe timeframe NOT NULL,
    open_time TIMESTAMPTZ NOT NULL,
    close_time TIMESTAMPTZ NOT NULL,
    open NUMERIC NOT NULL,
    high NUMERIC NOT NULL,
    low NUMERIC NOT NULL,
    close NUMERIC NOT NULL,
    volume NUMERIC NOT NULL,
    source provider NOT NULL,
    raw_payload JSONB NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT unique_asset_timeframe_close UNIQUE (asset_id, timeframe, close_time)
);
