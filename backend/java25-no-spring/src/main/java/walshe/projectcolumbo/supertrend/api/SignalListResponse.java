package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.signal.SignalSummary;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * @param lastIngestionAt finished-at of the last successful BINANCE ingestion run for the
 *                         requested timeframe; null if none has ever completed
 * @param candlesThrough   close time of the most recent stored candle for the requested timeframe; null if none
 * @param stale            true if the requested timeframe's candle data hasn't reached its
 *                         expected latest close time (not grace-adjusted - see FreshnessService)
 */
public record SignalListResponse(
        List<SignalSummary> signals,
        OffsetDateTime lastIngestionAt,
        OffsetDateTime candlesThrough,
        boolean stale
) {
    public SignalListResponse {
        Objects.requireNonNull(signals, "signals must not be null");
    }
}
