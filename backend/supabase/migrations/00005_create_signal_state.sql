-- 00005_create_signal_state.sql

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
