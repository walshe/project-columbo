package walshe.projectcolumbo.api.v1.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import walshe.projectcolumbo.persistence.IndicatorType;
import walshe.projectcolumbo.persistence.SignalEvent;

@Schema(description = "A single scan condition comprising an indicator and its expected event")
public record ScanCondition(
    @NotNull(message = "indicatorType is required")
    @Schema(description = "The type of indicator", example = "SUPERTREND")
    IndicatorType indicatorType,

    @NotNull(message = "event is required")
    @Schema(description = "The event associated with the indicator", example = "BULLISH_REVERSAL")
    SignalEvent event
) {}
