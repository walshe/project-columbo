package walshe.projectcolumbo.supertrend.freshness;

import java.time.Duration;

/**
 * Thrown by {@link FreshnessService#requireFresh} when a timeframe's data is stale beyond the
 * grace window. Intended to be mapped to a 503 response with a {@code Retry-After} header once
 * the HTTP layer exists (see openspec tasks.md group 9) - {@link #retryAfterSeconds()} is a
 * fixed, conservative value rather than a computed next-run estimate, since this module has no
 * knowledge of the pipeline's actual scheduling.
 */
public class StaleDataException extends RuntimeException {

    private static final Duration RETRY_AFTER = Duration.ofHours(1);

    private final FreshnessStatus status;

    public StaleDataException(FreshnessStatus status) {
        super(status.timeframe() + " data is stale: expected latest close time " + status.expectedLatestCloseTime()
                + ", actual " + status.actualLatestCloseTime());
        this.status = status;
    }

    public FreshnessStatus status() {
        return status;
    }

    public long retryAfterSeconds() {
        return RETRY_AFTER.toSeconds();
    }
}
