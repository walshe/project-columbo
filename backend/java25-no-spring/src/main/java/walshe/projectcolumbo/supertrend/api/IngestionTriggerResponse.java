package walshe.projectcolumbo.supertrend.api;

import java.util.Objects;

/** @param status acknowledgement status of the trigger request itself, not the run's eventual outcome */
public record IngestionTriggerResponse(long runId, String status) {
    public IngestionTriggerResponse {
        Objects.requireNonNull(status, "status must not be null");
    }
}
