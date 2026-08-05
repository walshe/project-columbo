-- Every asset onboarded so far is a Binance crypto pair, so DEFAULT 'CRYPTO' both backfills every
-- existing row in this same statement and covers any future insert that omits the column.
CREATE TYPE asset_class AS ENUM ('CRYPTO', 'STOCK', 'ETF', 'COMMODITY');

ALTER TABLE asset ADD COLUMN asset_class asset_class NOT NULL DEFAULT 'CRYPTO';

CREATE INDEX idx_asset_asset_class ON asset (asset_class);
