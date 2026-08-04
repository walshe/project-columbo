package walshe.projectcolumbo.supertrend.pipeline;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Terminal outcome of a pipeline run, passed as one unit to {@code IngestionRunDao.complete}
 * instead of separate positional parameters — several of the fields share a type (int counts),
 * so a parameter object removes the risk of transposing them at the call site.
 *
 * @param errorSample null if the run had no errors
 */
public record IngestionRunOutcome(
        IngestionRunStatus status,
        OffsetDateTime finishedAt,
        long durationMs,
        int insertedCount,
        int updatedCount,
        int skippedCount,
        int errorCount,
        String errorSample
) {
    public IngestionRunOutcome {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(finishedAt, "finishedAt must not be null");
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must not be negative, was: " + durationMs);
        }
        if (insertedCount < 0 || updatedCount < 0 || skippedCount < 0 || errorCount < 0) {
            throw new IllegalArgumentException("counts must not be negative: inserted=" + insertedCount
                    + " updated=" + updatedCount + " skipped=" + skippedCount + " error=" + errorCount);
        }
    }
}
