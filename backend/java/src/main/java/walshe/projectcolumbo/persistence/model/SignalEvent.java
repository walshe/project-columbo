package walshe.projectcolumbo.persistence.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A discrete state-change event on a candle. NONE means no change at that candle; the "
        + "remaining values mark a specific transition (SuperTrend reversal, RSI 60/40 cross, Elder Impulse "
        + "colour change, or Thermometer EMA cross / triple spike).")
public enum SignalEvent {
    NONE,
    SUPERTREND_BULLISH_REVERSAL,
    SUPERTREND_BEARISH_REVERSAL,
    RSI_CROSSED_ABOVE_60,
    RSI_CROSSED_BELOW_40,
    ELDER_IMPULSE_TURNED_GREEN,
    ELDER_IMPULSE_TURNED_RED,
    ELDER_IMPULSE_TURNED_NEUTRAL,
    ELDER_THERMOMETER_CROSSED_ABOVE_EMA,
    ELDER_THERMOMETER_CROSSED_BELOW_EMA,
    ELDER_THERMOMETER_TRIPLE_SPIKE
}
