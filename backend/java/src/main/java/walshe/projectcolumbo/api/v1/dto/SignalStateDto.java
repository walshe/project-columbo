package walshe.projectcolumbo.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import walshe.projectcolumbo.persistence.model.TrendState;
import java.time.OffsetDateTime;

@Schema(description = "An asset's current signal state on the requested timeframe, with flip recency, "
        + "liquidity, and price-change context.")
public record SignalStateDto(
    @Schema(description = "The asset's trading symbol", example = "BTCUSDT")
    String symbol,

    @Schema(description = "The asset's current trend state on the requested timeframe/indicator")
    TrendState trendState,

    @Schema(description = "Close time of the candle at which the trend last flipped. "
            + "Null if the asset has no recorded flip.")
    OffsetDateTime lastFlipTime,

    @Schema(description = "Whole calendar days (UTC) between the last flip and now. "
            + "Null if the asset has no recorded flip.", example = "3")
    Long daysSinceFlip,

    @Schema(description = "7-day average traded volume, used as a liquidity measure. "
            + "May be zero when volume data is unavailable.")
    java.math.BigDecimal avgVolume7d,

    @Schema(description = "Deep link to the asset's TradingView chart on the requested timeframe")
    String tradingviewUrl,

    @Schema(description = "Percentage price change from the flip candle's close to the most recent "
            + "finalized candle close, signed (positive = up since flip), rounded to 2 decimal places. "
            + "Null when there is no recorded flip or no candle exists at the flip time.", example = "12.34")
    java.math.BigDecimal pctChangeSinceFlip
) {}
