-- The plain index from V6 doesn't prevent two concurrent runDaily() calls from both passing
-- isRunning() and both inserting a RUNNING row (check-then-act race). Making it unique pushes
-- the actual guarantee down to the database; the in-app isRunning() check remains as a cheap
-- fast-path rejection only.
DROP INDEX idx_ingestion_run_running;
CREATE UNIQUE INDEX idx_ingestion_run_running ON ingestion_run (provider, timeframe) WHERE status = 'RUNNING';
