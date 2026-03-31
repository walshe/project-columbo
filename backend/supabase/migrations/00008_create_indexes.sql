-- 00008_create_indexes.sql
-- Consolidated signal_state indexes for lookups, flips, and scans

-- Optimize finding the latest finalized signal_state per asset
CREATE INDEX idx_signal_state_lookup
    ON signal_state (timeframe, indicator_type, asset_id, close_time DESC);

-- Optimize finding the latest flip per asset (events that are not NONE)
CREATE INDEX idx_signal_state_flips
    ON signal_state (timeframe, indicator_type, asset_id, close_time DESC)
    WHERE event != 'NONE';

-- Optimize market scans across all assets for the latest finalized candle
CREATE INDEX idx_signal_state_scan
    ON signal_state (indicator_type, event, timeframe, close_time DESC);
