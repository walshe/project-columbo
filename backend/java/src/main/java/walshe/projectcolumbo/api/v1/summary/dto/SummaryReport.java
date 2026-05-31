package walshe.projectcolumbo.api.v1.summary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import walshe.projectcolumbo.api.v1.dto.MarketPulseDto;
import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.api.v1.scan.dto.ScanResult;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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
    List<ScanResult> bearishRsiOversold,

    @Schema(description = "Timestamp when the most recent successful ingestion pipeline run finished. " +
            "Tells you when the data was last processed. Null if no successful run has been recorded yet.")
    OffsetDateTime lastIngestionAt,

    @Schema(description = "Calendar date (UTC) of the most recent daily candle in the database. " +
            "Tells you what trading day the signals and indicators are based on. " +
            "Null if no candles have been ingested yet.")
    LocalDate candlesThrough
) {}
