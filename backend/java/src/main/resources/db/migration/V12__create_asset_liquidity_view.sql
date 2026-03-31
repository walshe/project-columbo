CREATE OR REPLACE VIEW v_asset_liquidity AS
SELECT
    c.asset_id,
    AVG(c.volume) FILTER (WHERE c.close_time >= now() - INTERVAL '7 days') AS avg_volume_7d
FROM candle c
WHERE c.timeframe = 'D1'
GROUP BY c.asset_id;
