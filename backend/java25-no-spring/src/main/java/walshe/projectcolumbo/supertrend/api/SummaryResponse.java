package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.pulse.MarketBreadthSnapshot;
import walshe.projectcolumbo.supertrend.signal.SignalSummary;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * SuperTrend-only market summary for one timeframe: no RSI or any other indicator anywhere in
 * this shape - a deliberate breaking change from the old app's `/summary`, which mixed in RSI
 * scan results.
 *
 * @param pulse            latest market-breadth snapshot for the requested timeframe; null if none yet
 * @param bullishSignals   bullish assets, ordered by last-flip descending
 * @param bearishSignals   bearish assets, ordered by last-flip descending
 * @param lastIngestionAt  finished-at of the last successful BINANCE ingestion run for this timeframe
 * @param candlesThrough   close time of the most recent stored candle for this timeframe; null if none
 * @param stale            true if this timeframe hasn't reached its expected latest close time
 */
public record SummaryResponse(
        MarketBreadthSnapshot pulse,
        List<SignalSummary> bullishSignals,
        List<SignalSummary> bearishSignals,
        OffsetDateTime lastIngestionAt,
        OffsetDateTime candlesThrough,
        boolean stale
) {
}
