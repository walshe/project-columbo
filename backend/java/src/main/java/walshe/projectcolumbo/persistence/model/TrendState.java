package walshe.projectcolumbo.persistence.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The state an indicator reports for an asset on a timeframe. Values are namespaced by "
        + "indicator: SUPERTREND_* (trend direction, or UNKNOWN before warm-up), RSI_* (above 60 / below 40 / "
        + "neutral), ELDER_IMPULSE_* (GREEN long-permission / RED short-permission / NEUTRAL), and "
        + "ELDER_THERMOMETER_* (QUIET / HOT / SPIKE volatility regime).")
public enum TrendState {
    SUPERTREND_BULLISH,
    SUPERTREND_BEARISH,
    SUPERTREND_UNKNOWN,
    RSI_ABOVE_60,
    RSI_BELOW_40,
    RSI_NEUTRAL,
    ELDER_IMPULSE_GREEN,
    ELDER_IMPULSE_RED,
    ELDER_IMPULSE_NEUTRAL,
    ELDER_THERMOMETER_QUIET,
    ELDER_THERMOMETER_HOT,
    ELDER_THERMOMETER_SPIKE
}
