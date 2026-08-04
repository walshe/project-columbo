package walshe.projectcolumbo.supertrend.ingestion;

/** Accumulated outcome counts for an ingestion run. Immutable — combine via {@link #plus}. */
public record IngestionStats(int insertedCount, int updatedCount, int unchangedCount, int errorCount, String firstErrorMessage) {

    public IngestionStats {
        if (insertedCount < 0 || updatedCount < 0 || unchangedCount < 0 || errorCount < 0) {
            throw new IllegalArgumentException("counts must not be negative: inserted=" + insertedCount
                    + " updated=" + updatedCount + " unchanged=" + unchangedCount + " error=" + errorCount);
        }
    }

    public static final IngestionStats EMPTY = new IngestionStats(0, 0, 0, 0, null);

    static IngestionStats singleError(String message) {
        return new IngestionStats(0, 0, 0, 1, message);
    }

    IngestionStats plus(IngestionStats other) {
        return new IngestionStats(
                insertedCount + other.insertedCount,
                updatedCount + other.updatedCount,
                unchangedCount + other.unchangedCount,
                errorCount + other.errorCount,
                firstErrorMessage != null ? firstErrorMessage : other.firstErrorMessage
        );
    }
}
