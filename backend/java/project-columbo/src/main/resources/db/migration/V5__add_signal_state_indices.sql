-- V5__add_signal_state_indices.sql

-- Index to optimize finding the latest finalized signal_state per asset
CREATE INDEX idx_signal_state_lookup ON signal_state (timeframe, indicator_type, asset_id, close_time DESC);

-- Index to optimize finding the latest flip per asset
CREATE INDEX idx_signal_state_flips ON signal_state (timeframe, indicator_type, asset_id, close_time DESC) WHERE event != 'NONE';
