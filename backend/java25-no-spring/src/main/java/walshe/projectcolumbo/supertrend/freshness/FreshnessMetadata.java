package walshe.projectcolumbo.supertrend.freshness;

import java.time.OffsetDateTime;

/**
 * Reusable freshness fields for every in-scope read API response.
 *
 * @param lastSuccessfulIngestionAt null before any SUCCESS/PARTIAL ingestion run has ever completed
 * @param latestCandleDate          null before any candle has ever been stored
 */
public record FreshnessMetadata(OffsetDateTime lastSuccessfulIngestionAt, OffsetDateTime latestCandleDate) {
}
