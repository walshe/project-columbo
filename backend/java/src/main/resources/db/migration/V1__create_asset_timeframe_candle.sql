-- V1__create_asset_timeframe_candle.sql

-- 1. Create timeframe ENUM
CREATE TYPE timeframe AS ENUM ('D1');

-- 2. Create provider ENUM
CREATE TYPE provider AS ENUM ('BINANCE');

-- 3. Create asset table
CREATE TABLE asset (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR NOT NULL,               -- e.g. BTCUSDT
    name VARCHAR,
    provider provider NOT NULL,
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT unique_symbol_provider UNIQUE (symbol, provider)
);

-- 4. Create candle table
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
