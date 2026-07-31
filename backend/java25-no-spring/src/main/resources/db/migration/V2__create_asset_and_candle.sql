-- Timeframe and provider are defined with their full value set up front (no incremental
-- ALTER TYPE ADD VALUE history to replay from the old implementation).
CREATE TYPE timeframe AS ENUM ('D1', 'W1');
CREATE TYPE provider AS ENUM ('BINANCE');

CREATE TABLE asset (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR NOT NULL,
    name VARCHAR,
    provider provider NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT unique_symbol_provider UNIQUE (symbol, provider)
);

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
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT unique_asset_timeframe_close UNIQUE (asset_id, timeframe, close_time)
);
