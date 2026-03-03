-- V10__add_scan_index.sql

-- Index to optimize market scans across all assets for the latest finalized candle
CREATE INDEX idx_signal_state_scan ON signal_state (indicator_type, event, timeframe, close_time DESC);
