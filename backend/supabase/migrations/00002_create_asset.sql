-- 00002_create_asset.sql

CREATE TABLE asset (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR NOT NULL,
    name VARCHAR,
    provider provider NOT NULL,
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT unique_symbol_provider UNIQUE (symbol, provider)
);
