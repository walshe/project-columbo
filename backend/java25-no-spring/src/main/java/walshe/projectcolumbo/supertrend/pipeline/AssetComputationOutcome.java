package walshe.projectcolumbo.supertrend.pipeline;

/**
 * Outcome of one asset's per-run SuperTrend indicator or signal-state computation. Drives the
 * per-phase "computed N, skipped M (unchanged) of Z" summary and lets tests assert the
 * skip-when-nothing-new behavior directly without a mocking framework.
 */
public enum AssetComputationOutcome {

    /** At least one finalized candle newer than the last stored value - recomputed and upserted. */
    COMPUTED,

    /** A stored value exists and no finalized candle is newer than it - no candle load, no recompute, no upsert. */
    SKIPPED,

    /** The asset has no candles for this timeframe yet - nothing to compute from. */
    NO_CANDLES,

    /** Computation threw and was caught for per-asset isolation - logged as an error. */
    FAILED
}
