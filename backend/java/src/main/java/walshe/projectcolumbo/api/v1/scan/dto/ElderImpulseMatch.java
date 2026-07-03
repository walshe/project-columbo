package walshe.projectcolumbo.api.v1.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.SignalEvent;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.TrendState;

import java.time.OffsetDateTime;

// DISABLED: Elder Impulse System is not active — scan conditions on it are rejected, so this
// match type is not currently returned. Retained as a MatchedIndicator subtype for re-enablement.
@Schema(description = "Elder Impulse System match details (Elder disabled — not currently returned)")
public record ElderImpulseMatch(
    @Schema(description = "The type of indicator", example = "ELDER_IMPULSE")
    IndicatorType indicatorType,

    @Schema(description = "The timeframe this indicator was evaluated on", example = "1D")
    Timeframe timeframe,

    @Schema(description = "The Elder Impulse permission state", example = "ELDER_IMPULSE_GREEN",
            allowableValues = {"ELDER_IMPULSE_GREEN", "ELDER_IMPULSE_RED", "ELDER_IMPULSE_NEUTRAL"})
    TrendState state,

    @Schema(description = "The state change event, if any", example = "ELDER_IMPULSE_TURNED_GREEN")
    SignalEvent event,

    @Schema(description = "The number of days since the impulse state last changed", example = "2")
    int daysSinceChange,

    @Schema(description = "The close time of the candle when the state was evaluated")
    OffsetDateTime closeTime
) implements MatchedIndicator {}
