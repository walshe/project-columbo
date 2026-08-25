-- ingestion_run.provider has been a misleading label since add-tiingo-provider: a single run
-- already processes every active asset regardless of provider, so keying the single-flight
-- RUNNING lock by (provider, timeframe) let two differently-labeled triggers for the same
-- timeframe run fully concurrently over the same asset universe - the root cause of a systemic
-- burst of "duplicate key value violates unique constraint" errors on candle/indicator_supertrend.
-- The lock now covers timeframe alone, matching what actually needs to be exclusive.
DROP INDEX idx_ingestion_run_running;
DROP INDEX idx_ingestion_run_lookup;
ALTER TABLE ingestion_run DROP COLUMN provider;
CREATE UNIQUE INDEX idx_ingestion_run_running ON ingestion_run (timeframe) WHERE status = 'RUNNING';
CREATE INDEX idx_ingestion_run_lookup ON ingestion_run (timeframe, started_at DESC);
