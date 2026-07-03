package walshe.projectcolumbo.persistence.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Technical indicator a signal or scan condition refers to: SUPERTREND (trend), "
        + "RSI (momentum), ELDER_IMPULSE (Elder Impulse System), ELDER_THERMOMETER (Elder Market Thermometer).")
public enum IndicatorType {
    SUPERTREND,
    RSI,
    ELDER_IMPULSE,
    ELDER_THERMOMETER
}
