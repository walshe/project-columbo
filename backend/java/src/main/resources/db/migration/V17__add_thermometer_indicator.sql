-- V17__add_thermometer_indicator.sql
-- Market Thermometer indicator storage (Phase 7).
-- Stores daily temperature (volatility measure) and its 22-day EMA for each asset.
-- D1-only by design — no timeframe column.

CREATE TABLE indicator_thermometer (
    id              BIGSERIAL PRIMARY KEY,
    asset_id        BIGINT NOT NULL REFERENCES asset(id),
    close_time      TIMESTAMPTZ NOT NULL,
    temperature     NUMERIC(20,8) NOT NULL,
    temperature_ema NUMERIC(20,8),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_thermometer_asset_close UNIQUE (asset_id, close_time)
);

CREATE INDEX idx_thermometer_asset_close ON indicator_thermometer (asset_id, close_time DESC);

-- New indicator type
ALTER TYPE indicator_type ADD VALUE 'MARKET_THERMOMETER';

-- Thermometer categorical states
-- QUIET: temperature below EMA — good entry timing (low slippage)
-- HOT:   temperature above EMA — caution, slippage risk
-- SPIKE: temperature above 3× EMA — panic/euphoria, consider taking profits
ALTER TYPE trend_state ADD VALUE 'THERMOMETER_QUIET';
ALTER TYPE trend_state ADD VALUE 'THERMOMETER_HOT';
ALTER TYPE trend_state ADD VALUE 'THERMOMETER_SPIKE';

-- Thermometer signal transition events
ALTER TYPE signal_event ADD VALUE 'THERMOMETER_CROSSED_ABOVE_EMA';
ALTER TYPE signal_event ADD VALUE 'THERMOMETER_CROSSED_BELOW_EMA';
ALTER TYPE signal_event ADD VALUE 'THERMOMETER_TRIPLE_SPIKE';
