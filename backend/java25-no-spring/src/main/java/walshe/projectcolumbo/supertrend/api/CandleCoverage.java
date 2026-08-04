package walshe.projectcolumbo.supertrend.api;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * @param earliest       close time of the oldest stored candle for this timeframe; null if none
 * @param latest         close time of the newest stored candle for this timeframe; null if none
 * @param expectedLatest start of the most recent finalized period as of now - when {@code latest}
 *                       is at or after this, the timeframe has its most recently finalized candle
 * @param upToDate       true when {@code latest} has reached {@code expectedLatest}
 * @param assetCount     number of distinct assets with at least one stored candle in this timeframe
 */
public record CandleCoverage(
        OffsetDateTime earliest,
        OffsetDateTime latest,
        OffsetDateTime expectedLatest,
        boolean upToDate,
        long assetCount
) {
    public CandleCoverage {
        Objects.requireNonNull(expectedLatest, "expectedLatest must not be null");
        if (assetCount < 0) {
            throw new IllegalArgumentException("assetCount must not be negative, was: " + assetCount);
        }
    }
}
