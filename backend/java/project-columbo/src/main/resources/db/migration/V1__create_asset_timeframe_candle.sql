-- V1__create_asset_timeframe_candle.sql

-- 1. Create Postgres ENUM type timeframe
CREATE TYPE timeframe AS ENUM ('1D');

-- 2. Create asset table
CREATE TABLE asset (
    id BIGSERIAL PRIMARY KEY,
    provider_id VARCHAR NOT NULL,
    symbol VARCHAR UNIQUE,
    name VARCHAR,
    active BOOLEAN,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 3. Create candle table
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
    source VARCHAR NOT NULL,
    raw_payload JSONB NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    -- Add unique constraint
    CONSTRAINT unique_candle_idx UNIQUE (asset_id, timeframe, close_time)
);
