-- V6__create_ingestion_run.sql

-- 1. Create ingestion_run_status ENUM
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'ingestion_run_status') THEN
        CREATE TYPE ingestion_run_status AS ENUM (
            'RUNNING',
            'SUCCESS',
            'PARTIAL',
            'FAILED'
        );
    END IF;
END$$;

-- 2. Create ingestion_run table
CREATE TABLE ingestion_run (
    id BIGSERIAL PRIMARY KEY,
    provider provider NOT NULL,
    timeframe timeframe NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NULL,
    duration_ms BIGINT NULL,
    status ingestion_run_status NOT NULL,
    asset_count INT NOT NULL,
    inserted_count INT NOT NULL DEFAULT 0,
    updated_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    error_count INT NOT NULL DEFAULT 0,
    error_sample TEXT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 3. Add indexes
CREATE INDEX idx_ingestion_run_started_at ON ingestion_run (started_at DESC);
CREATE INDEX idx_ingestion_run_lookup ON ingestion_run (provider, timeframe, started_at DESC);
CREATE INDEX idx_ingestion_run_running ON ingestion_run (provider, timeframe) WHERE status = 'RUNNING';
