package walshe.projectcolumbo.api.v1.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import walshe.projectcolumbo.persistence.IndicatorType;
import walshe.projectcolumbo.persistence.SignalEvent;
import walshe.projectcolumbo.persistence.TrendState;

import java.time.OffsetDateTime;

@Schema(description = "Indicator event or state that matched the scan condition for an asset")
public record MatchedIndicator(
    @Schema(description = "The type of indicator", example = "SUPERTREND")
    IndicatorType indicatorType,

    @Schema(description = "The event associated with the indicator", example = "BULLISH_REVERSAL")
    SignalEvent event,

    @Schema(description = "The trend state associated with the indicator", example = "BULLISH")
    TrendState state,

    @Schema(description = "The number of days since the trend state flipped", example = "3")
    Integer flippedDaysAgo,

    @Schema(description = "The close time of the candle when the event or state match occurred")
    OffsetDateTime closeTime
) {}
