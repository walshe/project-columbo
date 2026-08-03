package walshe.projectcolumbo.supertrend.api;

/** @param status acknowledgement status of the trigger request itself, not the run's eventual outcome */
public record IngestionTriggerResponse(long runId, String status) {
}
