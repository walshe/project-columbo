package walshe.projectcolumbo.api.v1.dto;

public record IngestionResponse(
    Long runId,
    String status
) {
}
