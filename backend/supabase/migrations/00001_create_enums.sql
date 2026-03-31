-- 00001_create_enums.sql
-- Consolidated ENUMs for the Supabase implementation (RSI excluded)

CREATE TYPE timeframe AS ENUM ('D1');

CREATE TYPE provider AS ENUM ('BINANCE');

CREATE TYPE supertrend_direction AS ENUM ('UP', 'DOWN');

CREATE TYPE indicator_type AS ENUM ('SUPERTREND');

CREATE TYPE trend_state AS ENUM ('BULLISH', 'BEARISH', 'UNKNOWN');

CREATE TYPE signal_event AS ENUM (
    'NONE',
    'BULLISH_REVERSAL',
    'BEARISH_REVERSAL'
);

CREATE TYPE ingestion_run_status AS ENUM (
    'RUNNING',
    'SUCCESS',
    'PARTIAL',
    'FAILED'
);
