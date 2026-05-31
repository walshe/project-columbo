package walshe.projectcolumbo.api.v1.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "Response containing the results of a market scan")
public record ScanResponse(
    @Schema(description = "The logical operator used to combine conditions")
    ScanOperator operator,

    @Schema(description = "The original conditions evaluated, each with its timeframe")
    List<ScanCondition> conditions,

    @Schema(description = "The list of assets matching the scan conditions")
    List<ScanResult> results,

    @Schema(description = "Timestamp when the most recent successful ingestion pipeline run finished. " +
            "Tells you when the data was last processed. Null if no successful run has been recorded yet.")
    OffsetDateTime lastIngestionAt,

    @Schema(description = "Calendar date (UTC) of the most recent daily candle in the database. " +
            "Tells you what trading day the signals and indicators are based on. " +
            "Null if no candles have been ingested yet.")
    LocalDate candlesThrough
) {}
