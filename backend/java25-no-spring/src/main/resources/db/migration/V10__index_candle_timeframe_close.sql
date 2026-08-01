-- CandleDao.findLatestCloseTimeAcrossAllAssets (data-freshness) queries MAX(close_time) WHERE
-- timeframe = ?, which the existing unique index on (asset_id, timeframe, close_time) can't serve
-- efficiently since asset_id is the leading column.
CREATE INDEX idx_candle_timeframe_close ON candle (timeframe, close_time DESC);
