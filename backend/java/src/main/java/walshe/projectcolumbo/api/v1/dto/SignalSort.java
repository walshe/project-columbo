package walshe.projectcolumbo.api.v1.dto;

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
