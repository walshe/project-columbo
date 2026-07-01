package walshe.projectcolumbo.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "List of signal states with data-freshness metadata")
public record SignalListResponse(

        @Schema(description = "The signal states matching the requested filters")
        List<SignalStateDto> signals,

        @Schema(description = "Timestamp when the most recent successful ingestion pipeline run finished. " +
                "Tells you when the data was last processed. Null if no successful run has been recorded yet.")
        OffsetDateTime lastIngestionAt,

        @Schema(description = "Calendar date (UTC) of the most recent daily candle in the database. " +
                "Tells you what trading day the signal states are based on. " +
                "Null if no candles have been ingested yet.")
        LocalDate candlesThrough,

        @Schema(description = "True when the requested timeframe is missing its most recent finalized " +
                "candle (data is behind). Same determination as /candles/coverage upToDate.")
        boolean stale
) {}
