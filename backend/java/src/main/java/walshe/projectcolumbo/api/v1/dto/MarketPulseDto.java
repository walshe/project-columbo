package walshe.projectcolumbo.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "Market breadth snapshot for a timeframe/indicator — how many assets are bullish, "
        + "bearish, or without a state as of a given candle close.")
public record MarketPulseDto(
    @Schema(description = "Close time of the candle this breadth snapshot was computed for")
    OffsetDateTime snapshotCloseTime,

    @Schema(description = "Number of active assets in the bullish state", example = "27")
    int bullishCount,

    @Schema(description = "Number of active assets in the bearish state", example = "15")
    int bearishCount,

    @Schema(description = "Number of active assets with no computed state (e.g. insufficient history)", example = "3")
    int missingCount,

    @Schema(description = "Total number of active assets considered in this snapshot", example = "45")
    int totalAssets,

    @Schema(description = "Fraction of assets that are bullish (bullishCount / totalAssets), 0.0–1.0", example = "0.60")
    BigDecimal bullishRatio
) {}
