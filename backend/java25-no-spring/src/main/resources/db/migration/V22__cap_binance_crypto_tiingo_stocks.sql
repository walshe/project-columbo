-- Retires the tokenized Binance STOCK/ETF approach entirely - Tiingo's real equities (V17) now
-- own that domain, and mixing incompatible providers' assets in one pipeline run at this scale
-- was a real contributor to HikariCP pool exhaustion during a full backfill (247 assets fanning
-- out to 247 concurrent virtual threads against a 10-connection pool). Binance is now
-- crypto-only, capped to the earliest 50 onboarded (by id, i.e. insertion order); Tiingo is
-- capped to 50 too (currently 47 seeded, so this is a no-op today but a guard against future
-- growth re-introducing the same fan-out pressure).
--
-- Deactivation (not deletion), matching this project's established self-heal-via-active
-- convention (see V14) - fully reversible if ever needed, and preserves historical candle/
-- indicator data for the deactivated assets rather than orphaning it.
UPDATE asset SET active = false
WHERE provider = 'BINANCE' AND asset_class IN ('STOCK', 'ETF');

UPDATE asset SET active = false
WHERE provider = 'BINANCE' AND asset_class = 'CRYPTO' AND id NOT IN (
    SELECT id FROM asset WHERE provider = 'BINANCE' AND asset_class = 'CRYPTO' ORDER BY id ASC LIMIT 50
);

UPDATE asset SET active = false
WHERE provider = 'TIINGO' AND id NOT IN (
    SELECT id FROM asset WHERE provider = 'TIINGO' ORDER BY id ASC LIMIT 50
);
