package walshe.projectcolumbo.api.v1.summary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import walshe.projectcolumbo.api.v1.dto.MarketPulseDto;
import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.api.v1.scan.dto.ScanResult;

import java.util.List;

@Schema(description = "Aggregate report containing market pulse, trending signals, and scan results")
public record SummaryReport(
    @Schema(description = "Overall market sentiment pulse")
    MarketPulseDto pulse,

    @Schema(description = "Assets that recently flipped bullish")
    List<SignalStateDto> bullishSignals,

    @Schema(description = "Assets that recently flipped bearish")
    List<SignalStateDto> bearishSignals,

    @Schema(description = "Bullish assets with RSI crossing above 60")
    List<ScanResult> bullishRsiOverbought,

    @Schema(description = "Bearish assets with RSI crossing below 40")
    List<ScanResult> bearishRsiOversold
) {}
