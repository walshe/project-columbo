package walshe.projectcolumbo.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Acknowledgement of a triggered ingestion pipeline run")
public record IngestionResponse(
    @Schema(description = "Identifier of the created ingestion run record, for status lookup", example = "42")
    Long runId,

    @Schema(description = "Acknowledgement status of the trigger request", example = "STARTED")
    String status
) {
}
