package walshe.projectcolumbo.supertrend.freshness;

import walshe.projectcolumbo.supertrend.shared.FinalizedBoundary;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.time.OffsetDateTime;

/**
 * The close time a timeframe's latest stored candle is expected to have reached by now, absent
 * any pipeline delay: D1 candles finalize daily, so the latest one due is yesterday's; W1
 * candles finalize weekly, so the latest one due is from seven days ago.
 */
public final class FreshnessBoundary {

    private FreshnessBoundary() {
    }

    public static OffsetDateTime expectedLatestCloseTime(Timeframe timeframe, OffsetDateTime now) {
        OffsetDateTime utcMidnightToday = FinalizedBoundary.utcMidnightToday(now);
        return switch (timeframe) {
            case D1 -> utcMidnightToday.minusDays(1);
            case W1 -> utcMidnightToday.minusDays(7);
        };
    }
}
