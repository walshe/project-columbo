-- asset_class is nullable: NULL means "combined across every class" (this table's only meaning
-- until now), a specific class means "this class only". Standard SQL treats every NULL as
-- distinct from every other NULL, so a plain UNIQUE(timeframe, snapshot_close_time, asset_class)
-- constraint would let multiple "combined" rows accumulate for the same (timeframe,
-- snapshot_close_time) instead of upserting into one.
--
-- A COALESCE(asset_class::text, '__ALL__')-based expression index was the first approach tried,
-- but Postgres rejects it: an enum's ::text cast goes through the type's output function, which
-- is STABLE (its result can depend on catalog state - new enum labels can be added later), not
-- IMMUTABLE, and index expressions must be IMMUTABLE. Two partial unique indexes - one for the
-- NULL/combined case, one for the non-NULL/per-class case - sidestep the problem entirely: no
-- function call in either index, just a WHERE predicate, so each row is only ever compared
-- against rows of its own kind.
ALTER TABLE market_breadth_snapshot ADD COLUMN asset_class asset_class;

ALTER TABLE market_breadth_snapshot DROP CONSTRAINT unique_market_breadth_snapshot;

CREATE UNIQUE INDEX unique_market_breadth_snapshot_combined
    ON market_breadth_snapshot (timeframe, snapshot_close_time)
    WHERE asset_class IS NULL;

CREATE UNIQUE INDEX unique_market_breadth_snapshot_per_class
    ON market_breadth_snapshot (timeframe, snapshot_close_time, asset_class)
    WHERE asset_class IS NOT NULL;

DROP INDEX idx_market_breadth_lookup;

CREATE INDEX idx_market_breadth_lookup ON market_breadth_snapshot (timeframe, asset_class, snapshot_close_time DESC);
