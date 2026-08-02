package walshe.projectcolumbo.supertrend.signal;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * @param lastFlipTime         close time of the most recent flip (event != NONE); null if the
 *                              asset has never flipped on this timeframe
 * @param avgVolume7d           rolling 7-day average D1 volume; zero if unavailable
 * @param pctChangeSinceFlip    signed, 2dp; null if there's no flip or no matching candle close
 */
public record SignalSummary(
        String symbol,
        TrendState trendState,
        OffsetDateTime lastFlipTime,
        BigDecimal avgVolume7d,
        BigDecimal pctChangeSinceFlip
) {
}
