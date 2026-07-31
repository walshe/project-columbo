-- No indicator_type column: this system has exactly one indicator (SuperTrend), so the
-- multi-indicator-ready dimension from the old schema is dropped rather than kept-but-constrained.
CREATE TYPE trend_state AS ENUM ('BULLISH', 'BEARISH', 'UNKNOWN');
CREATE TYPE signal_event AS ENUM ('NONE', 'BULLISH_REVERSAL', 'BEARISH_REVERSAL');

CREATE TABLE signal_state (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL REFERENCES asset(id),
    timeframe timeframe NOT NULL,
    close_time TIMESTAMPTZ NOT NULL,
    trend_state trend_state NOT NULL,
    event signal_event NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT unique_signal_state_asset_timeframe_close UNIQUE (asset_id, timeframe, close_time)
);

-- Optimizes finding the latest signal_state per asset.
CREATE INDEX idx_signal_state_lookup ON signal_state (timeframe, asset_id, close_time DESC);

-- Optimizes finding the latest flip per asset.
CREATE INDEX idx_signal_state_flips ON signal_state (timeframe, asset_id, close_time DESC) WHERE event != 'NONE';
