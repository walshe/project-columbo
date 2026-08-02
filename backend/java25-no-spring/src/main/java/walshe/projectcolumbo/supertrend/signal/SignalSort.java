package walshe.projectcolumbo.supertrend.signal;

/**
 * Orderings for {@code /api/v1/signals}. There is no {@code LIQUIDITY_ASC} - only descending is
 * offered, since liquidity sort exists to surface the most-tradeable assets first. Pct-change
 * direction is caller-chosen, not inferred from a {@code state} filter.
 */
public enum SignalSort {
    ASSET_ASC,
    LAST_FLIP_ASC,
    LAST_FLIP_DESC,
    TREND_STATE_ASC,
    LIQUIDITY_DESC,
    PCT_CHANGE_ASC,
    PCT_CHANGE_DESC
}
