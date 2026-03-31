-- V3__create_signal_state.sql

-- 1. Create ENUM type indicator_type
CREATE TYPE indicator_type AS ENUM ('SUPERTREND');

-- 2. Create ENUM type trend_state
CREATE TYPE trend_state AS ENUM ('BULLISH', 'BEARISH');

-- 3. Create ENUM type signal_event
CREATE TYPE signal_event AS ENUM (
    'NONE',
    'BULLISH_REVERSAL',
    'BEARISH_REVERSAL'
);

-- 4. Create table signal_state
CREATE TABLE signal_state (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL REFERENCES asset(id),
    timeframe timeframe NOT NULL,
    indicator_type indicator_type NOT NULL,
    close_time TIMESTAMPTZ NOT NULL,
    trend_state trend_state NOT NULL,
    event signal_event NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT unique_signal_state_asset_timeframe_indicator_close UNIQUE (asset_id, timeframe, indicator_type, close_time)
);
