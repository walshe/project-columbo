package walshe.projectcolumbo.supertrend.shared;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * The single UTC-midnight-today boundary used everywhere a candle needs to be judged
 * "finalized" (ingestion end time, rollup completeness, freshness checks) — one shared
 * utility so this boundary is computed identically across every consumer.
 */
public final class FinalizedBoundary {

    private FinalizedBoundary() {
    }

    /** UTC midnight of the current day, relative to {@code now}. Anything at or after this is not yet finalized. */
    public static OffsetDateTime utcMidnightToday(OffsetDateTime now) {
        return now.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    }
}
