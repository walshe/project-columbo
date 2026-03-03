package walshe.projectcolumbo.api.v1.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import walshe.projectcolumbo.persistence.IndicatorType;
import walshe.projectcolumbo.persistence.SignalEvent;

import java.time.OffsetDateTime;

@Schema(description = "RSI match details")
public record RsiMatch(
    @Schema(description = "The type of indicator", example = "RSI")
    IndicatorType indicatorType,

    @Schema(description = "The event associated with the indicator", example = "CROSSED_ABOVE_60")
    SignalEvent event,

    @Schema(description = "The RSI value at the time of match", example = "62.4")
    double rsiValue,

    @Schema(description = "The number of days since the last cross", example = "0")
    int daysSinceCross,

    @Schema(description = "The close time of the candle when the match occurred")
    OffsetDateTime closeTime
) implements MatchedIndicator {}
