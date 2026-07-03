package walshe.projectcolumbo.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ordering for signal list results. LAST_FLIP_* orders by flip recency, ASSET_ASC "
        + "alphabetically, TREND_STATE_ASC by trend state, LIQUIDITY_DESC by 7-day volume, and "
        + "PCT_CHANGE_* by percentage change since flip (nulls last). Percentage-change sorting is "
        + "trend-relative: use DESC for a bullish list (biggest gain first) and ASC for a bearish list "
        + "(biggest drop first).")
public enum SignalSort {
    LAST_FLIP_ASC,
    LAST_FLIP_DESC,
    ASSET_ASC,
    TREND_STATE_ASC,
    LIQUIDITY_DESC,
    // Percentage price change since the flip candle. Signed, so the caller picks the
    // direction that means "trend confirming most strongly": DESC for a bullish list
    // (biggest gain on top), ASC for a bearish list (biggest drop on top). Assets with
    // no recorded flip (null pct) always sort last.
    PCT_CHANGE_ASC,
    PCT_CHANGE_DESC
}
