package walshe.projectcolumbo.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "Returned with HTTP 503 when requireFresh=true and the data is stale beyond the grace window")
public record StaleDataError(
    @Schema(description = "Human-readable explanation")
    String message,

    @Schema(description = "The timeframe whose data is stale")
    String timeframe,

    @Schema(description = "Start of the most recent finalized period that the data has not reached")
    OffsetDateTime expectedLatest
) {}
