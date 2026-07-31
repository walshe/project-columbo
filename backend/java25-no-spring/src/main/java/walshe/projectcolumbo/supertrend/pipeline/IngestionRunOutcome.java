package walshe.projectcolumbo.supertrend.pipeline;

import java.time.OffsetDateTime;

/**
 * Terminal outcome of a pipeline run, passed as one unit to {@code IngestionRunDao.complete}
 * instead of separate positional parameters — several of the fields share a type (int counts),
 * so a parameter object removes the risk of transposing them at the call site.
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
}
