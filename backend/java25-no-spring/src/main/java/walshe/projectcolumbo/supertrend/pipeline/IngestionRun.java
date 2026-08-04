package walshe.projectcolumbo.supertrend.pipeline;

import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * @param finishedAt null while the run is still RUNNING
 * @param durationMs null while the run is still RUNNING
 * @param errorSample null if the run has had no errors so far
 */
public record IngestionRun(
        long id,
        Provider provider,
        Timeframe timeframe,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Long durationMs,
        IngestionRunStatus status,
        int assetCount,
        int insertedCount,
        int updatedCount,
        int skippedCount,
        int errorCount,
        String errorSample
) {
    public IngestionRun {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(timeframe, "timeframe must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (assetCount < 0 || insertedCount < 0 || updatedCount < 0 || skippedCount < 0 || errorCount < 0) {
            throw new IllegalArgumentException("counts must not be negative: assets=" + assetCount
                    + " inserted=" + insertedCount + " updated=" + updatedCount
                    + " skipped=" + skippedCount + " error=" + errorCount);
        }
    }
}
