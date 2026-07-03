package walshe.projectcolumbo.api.v1.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.TrendState;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

// DISABLED: Market Thermometer is not active — scan conditions on it are rejected, so this
// match type is not currently returned. Retained as a MatchedIndicator subtype for re-enablement.
@Schema(description = "Market Thermometer match details (Elder disabled — not currently returned)")
public record ThermometerMatch(
    @Schema(description = "The type of indicator", example = "ELDER_THERMOMETER")
    IndicatorType indicatorType,

    @Schema(description = "The timeframe this indicator was evaluated on", example = "D1")
    Timeframe timeframe,

    @Schema(description = "The thermometer categorical state",
            allowableValues = {"ELDER_THERMOMETER_QUIET", "ELDER_THERMOMETER_HOT", "ELDER_THERMOMETER_SPIKE"})
    TrendState state,

    @Schema(description = "Raw temperature value — daily bar range extension beyond yesterday")
    BigDecimal temperature,

    @Schema(description = "22-day EMA of temperature (signal line). Profit target: long = yesterday high + EMA; short = yesterday low - EMA")
    BigDecimal temperatureEma,

    @Schema(description = "The close time of the candle when the state was evaluated")
    OffsetDateTime closeTime
) implements MatchedIndicator {}
