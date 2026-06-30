-- Add version columns for optimistic locking to support parallel indicator computation

ALTER TABLE indicator_supertrend ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE indicator_rsi ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
