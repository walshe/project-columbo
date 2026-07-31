package walshe.projectcolumbo.supertrend.pipeline;

import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.time.OffsetDateTime;

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
}
