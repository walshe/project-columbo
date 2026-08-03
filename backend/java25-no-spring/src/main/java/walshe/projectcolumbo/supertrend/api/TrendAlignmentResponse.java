package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.signal.SignalSummary;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * @param maxRetestAgeDays the requested retest-age cutoff this report was computed with - included
 *                         so the response (and its Markdown rendering) is self-describing rather
 *                         than requiring the reader to already know what request produced it
 * @param lastIngestionAt finished-at of the last successful BINANCE D1 ingestion run - D1 is the
 *                         driving timeframe for this cross-timeframe report (W1 is rolled up from
 *                         D1), so freshness is evaluated against D1 only, never W1
 * @param candlesThrough   close time of the most recent stored D1 candle; null if none
 * @param stale            true if D1 hasn't reached its expected latest close time
 */
public record TrendAlignmentResponse(
        int maxRetestAgeDays,
        List<SignalSummary> bullishConfluence,
        List<SignalSummary> bullishRetest,
        List<SignalSummary> bearishConfluence,
        List<SignalSummary> bearishRetest,
        OffsetDateTime lastIngestionAt,
        OffsetDateTime candlesThrough,
        boolean stale
) {
}
