-- V4__create_market_breadth_snapshot.sql

CREATE TABLE market_breadth_snapshot (
    id BIGSERIAL PRIMARY KEY,
    timeframe timeframe NOT NULL,
    indicator_type indicator_type NOT NULL,
    snapshot_close_time TIMESTAMPTZ NOT NULL,
    bullish_count INT NOT NULL,
    bearish_count INT NOT NULL,
    missing_count INT NOT NULL,
    total_assets INT NOT NULL,
    bullish_ratio NUMERIC(5, 4) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT unique_market_breadth_snapshot UNIQUE (timeframe, indicator_type, snapshot_close_time)
);

CREATE INDEX idx_market_breadth_lookup ON market_breadth_snapshot (timeframe, indicator_type, snapshot_close_time DESC);
