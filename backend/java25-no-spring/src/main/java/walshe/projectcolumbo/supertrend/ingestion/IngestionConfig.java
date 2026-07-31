package walshe.projectcolumbo.supertrend.ingestion;

import java.time.OffsetDateTime;

/** Ingestion configuration, sourced from environment variables. */
public record IngestionConfig(OffsetDateTime backfillStart) {

    private static final String ENV_BACKFILL_START = "SUPERTREND_BACKFILL_START";

    public static IngestionConfig fromEnvironment() {
        String value = System.getenv(ENV_BACKFILL_START);
        OffsetDateTime backfillStart = (value == null || value.isBlank()) ? null : OffsetDateTime.parse(value);
        return new IngestionConfig(backfillStart);
    }
}
