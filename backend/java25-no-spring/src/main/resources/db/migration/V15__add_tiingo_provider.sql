-- Must be its own migration: Postgres forbids using a new enum value in the same transaction
-- that adds it, so the seed migration inserting TIINGO-provider rows (V17) has to come later.
ALTER TYPE provider ADD VALUE 'TIINGO';
