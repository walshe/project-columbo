package walshe.projectcolumbo.api.v1.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Scan result for a single asset")
public record ScanResult(
    @Schema(description = "The symbol of the matched asset", example = "BTCUSDT")
    String assetSymbol,

    @Schema(description = "List of indicator events that matched the scan conditions")
    List<MatchedIndicator> matchedIndicators,

    @Schema(description = "The TradingView chart URL for the asset", example = "https://www.tradingview.com/chart/?symbol=BINANCE+BTCUSDT&interval=1D")
    String tradingviewUrl
) {}
