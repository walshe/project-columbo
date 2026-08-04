package walshe.projectcolumbo.supertrend.pipeline;

import java.util.Objects;

public record PipelineRunResult(long runId, IngestionRunStatus status) {
    public PipelineRunResult {
        Objects.requireNonNull(status, "status must not be null");
    }
}
