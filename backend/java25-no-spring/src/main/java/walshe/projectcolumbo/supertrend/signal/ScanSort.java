package walshe.projectcolumbo.supertrend.signal;

/**
 * Orderings for {@code /api/v1/scan}. There is no {@code LIQUIDITY_ASC} - only descending is
 * offered, since liquidity sort exists to surface the most-tradeable matches first (same
 * rationale as {@link SignalSort#LIQUIDITY_DESC}).
 */
public enum ScanSort {
    SYMBOL_ASC,
    LIQUIDITY_DESC
}
