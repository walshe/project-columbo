package walshe.projectcolumbo.api.v1.dto;

import walshe.projectcolumbo.ingestion.IngestionRunStatus;

public record IngestionResponse(
    Long runId,
    IngestionRunStatus status
) {
}
