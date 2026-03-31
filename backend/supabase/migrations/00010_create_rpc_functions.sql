-- 00010_create_rpc_functions.sql
-- PostgreSQL functions callable via supabase.rpc()

-- =============================================================================
-- get_signals: Replaces Java SignalQueryService.listSignals()
-- Returns latest signal state per active asset with flip time and liquidity
-- =============================================================================
CREATE OR REPLACE FUNCTION get_signals(
    p_timeframe timeframe,
    p_indicator_type indicator_type,
    p_state_filter trend_state DEFAULT NULL,
    p_sort TEXT DEFAULT 'ASSET_ASC'
)
RETURNS TABLE (
    symbol VARCHAR,
    trend_state trend_state,
    event signal_event,
    close_time TIMESTAMPTZ,
    last_flip_time TIMESTAMPTZ,
    days_since_flip BIGINT,
    avg_volume_7d NUMERIC,
    tradingview_url TEXT
)
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_boundary TIMESTAMPTZ := date_trunc('day', now() AT TIME ZONE 'UTC');
BEGIN
    RETURN QUERY
    WITH latest_signals AS (
        -- Latest signal_state per active asset (max close_time < boundary)
        SELECT DISTINCT ON (s.asset_id)
            s.asset_id,
            a.symbol AS asset_symbol,
            s.trend_state AS ts,
            s.event AS evt,
            s.close_time AS ct
        FROM signal_state s
        JOIN asset a ON a.id = s.asset_id
        WHERE a.active = true
          AND s.timeframe = p_timeframe
          AND s.indicator_type = p_indicator_type
          AND s.close_time < v_boundary
        ORDER BY s.asset_id, s.close_time DESC
    ),
    latest_flips AS (
        -- Latest flip (event != NONE) per active asset
        SELECT DISTINCT ON (s.asset_id)
            s.asset_id,
            s.close_time AS flip_time
        FROM signal_state s
        JOIN asset a ON a.id = s.asset_id
        WHERE a.active = true
          AND s.timeframe = p_timeframe
          AND s.indicator_type = p_indicator_type
          AND s.event != 'NONE'
          AND s.close_time < v_boundary
        ORDER BY s.asset_id, s.close_time DESC
    ),
    liquidity AS (
        SELECT v.asset_id, v.avg_volume_7d AS vol_7d
        FROM v_asset_liquidity v
    )
    SELECT
        ls.asset_symbol AS symbol,
        ls.ts AS trend_state,
        ls.evt AS event,
        ls.ct AS close_time,
        lf.flip_time AS last_flip_time,
        CASE
            WHEN lf.flip_time IS NOT NULL
            THEN EXTRACT(DAY FROM (now() - lf.flip_time))::BIGINT
            ELSE NULL
        END AS days_since_flip,
        COALESCE(liq.vol_7d, 0) AS avg_volume_7d,
        'https://www.tradingview.com/chart/?symbol=BINANCE:' || ls.asset_symbol AS tradingview_url
    FROM latest_signals ls
    LEFT JOIN latest_flips lf ON lf.asset_id = ls.asset_id
    LEFT JOIN liquidity liq ON liq.asset_id = ls.asset_id
    WHERE (p_state_filter IS NULL OR ls.ts = p_state_filter)
    ORDER BY
        CASE p_sort
            WHEN 'ASSET_ASC' THEN ls.asset_symbol
        END ASC,
        CASE p_sort
            WHEN 'LAST_FLIP_ASC' THEN lf.flip_time
        END ASC NULLS LAST,
        CASE p_sort
            WHEN 'LAST_FLIP_DESC' THEN lf.flip_time
        END DESC NULLS LAST,
        CASE p_sort
            WHEN 'TREND_STATE_ASC' THEN ls.ts::TEXT
        END ASC,
        CASE p_sort
            WHEN 'LIQUIDITY_DESC' THEN COALESCE(liq.vol_7d, 0)
        END DESC;
END;
$$;

-- =============================================================================
-- get_market_pulse_latest: Returns the most recent market breadth snapshot
-- =============================================================================
CREATE OR REPLACE FUNCTION get_market_pulse_latest(
    p_timeframe timeframe,
    p_indicator_type indicator_type
)
RETURNS TABLE (
    snapshot_close_time TIMESTAMPTZ,
    bullish_count INT,
    bearish_count INT,
    missing_count INT,
    total_assets INT,
    bullish_ratio NUMERIC(5, 4)
)
LANGUAGE sql
STABLE
AS $$
    SELECT
        m.snapshot_close_time,
        m.bullish_count,
        m.bearish_count,
        m.missing_count,
        m.total_assets,
        m.bullish_ratio
    FROM market_breadth_snapshot m
    WHERE m.timeframe = p_timeframe
      AND m.indicator_type = p_indicator_type
    ORDER BY m.snapshot_close_time DESC
    LIMIT 1;
$$;

-- =============================================================================
-- get_market_pulse_history: Returns breadth snapshots within a date range
-- =============================================================================
CREATE OR REPLACE FUNCTION get_market_pulse_history(
    p_timeframe timeframe,
    p_indicator_type indicator_type,
    p_from TIMESTAMPTZ,
    p_to TIMESTAMPTZ
)
RETURNS TABLE (
    snapshot_close_time TIMESTAMPTZ,
    bullish_count INT,
    bearish_count INT,
    missing_count INT,
    total_assets INT,
    bullish_ratio NUMERIC(5, 4)
)
LANGUAGE sql
STABLE
AS $$
    SELECT
        m.snapshot_close_time,
        m.bullish_count,
        m.bearish_count,
        m.missing_count,
        m.total_assets,
        m.bullish_ratio
    FROM market_breadth_snapshot m
    WHERE m.timeframe = p_timeframe
      AND m.indicator_type = p_indicator_type
      AND m.snapshot_close_time >= p_from
      AND m.snapshot_close_time <= p_to
    ORDER BY m.snapshot_close_time ASC;
$$;
