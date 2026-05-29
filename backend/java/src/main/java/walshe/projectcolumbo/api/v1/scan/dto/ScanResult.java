package walshe.projectcolumbo.api.v1.scan.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Scan result for a single asset")
public record ScanResult(
    @Schema(description = "The symbol of the matched asset", example = "BTCUSDT")
    String assetSymbol,

    @ArraySchema(schema = @Schema(
        description = "Indicator event or state that matched the scan condition",
        oneOf = {SupertrendMatch.class, RsiMatch.class},
        discriminatorProperty = "indicatorType",
        discriminatorMapping = {
            @DiscriminatorMapping(value = "SUPERTREND", schema = SupertrendMatch.class),
            @DiscriminatorMapping(value = "RSI", schema = RsiMatch.class)
        }
    ))
    List<MatchedIndicator> matchedIndicators,

    @Schema(description = "The 7-day average volume for the asset")
    java.math.BigDecimal avgVolume7d,

    @Schema(description = "The TradingView chart URL for the asset", example = "https://www.tradingview.com/chart/?symbol=BINANCE+BTCUSDT&interval=1D")
    String tradingviewUrl
) {}
