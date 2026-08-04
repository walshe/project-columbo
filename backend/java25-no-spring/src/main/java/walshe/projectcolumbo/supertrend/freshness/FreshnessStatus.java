package walshe.projectcolumbo.supertrend.freshness;

import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * @param actualLatestCloseTime  null if no candle has ever been stored for this timeframe
 * @param upToDate               true if the actual latest close time has reached the expected boundary
 * @param staleBeyondGraceWindow true once more than the grace window has elapsed since the expected
 *                               boundary while still not up to date - see {@link FreshnessService}
 */
public record FreshnessStatus(
        Timeframe timeframe,
        OffsetDateTime expectedLatestCloseTime,
        OffsetDateTime actualLatestCloseTime,
        boolean upToDate,
        boolean staleBeyondGraceWindow
) {
    public FreshnessStatus {
        Objects.requireNonNull(timeframe, "timeframe must not be null");
        Objects.requireNonNull(expectedLatestCloseTime, "expectedLatestCloseTime must not be null");
    }
}
