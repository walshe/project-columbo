package walshe.projectcolumbo.api.v1.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import walshe.projectcolumbo.persistence.IndicatorType;
import walshe.projectcolumbo.persistence.SignalEvent;
import walshe.projectcolumbo.persistence.TrendState;

@Schema(description = "A single scan condition comprising an indicator and its expected event or state")
public record ScanCondition(
    @NotNull(message = "indicatorType is required")
    @Schema(description = "The type of indicator", example = "SUPERTREND")
    IndicatorType indicatorType,

    @Schema(description = "The event associated with the indicator", example = "BULLISH_REVERSAL")
    SignalEvent event,

    @Schema(description = "The trend state associated with the indicator", example = "BULLISH")
    TrendState state,

    @Schema(description = "The maximum number of days since the trend state flipped", example = "5")
    Integer maxDaysSinceFlip
) {}
