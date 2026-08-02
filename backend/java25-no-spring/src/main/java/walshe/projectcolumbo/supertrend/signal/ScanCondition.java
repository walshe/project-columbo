package walshe.projectcolumbo.supertrend.signal;

import walshe.projectcolumbo.supertrend.shared.Timeframe;

/**
 * One condition in a {@link ScanRequest}. Implicitly targets SuperTrend - there is no
 * indicator-type field, since SuperTrend is the only indicator in this system (a behavior change
 * from the prior implementation, where conditions could target RSI and combine cross-indicator).
 *
 * @param maxDaysSinceFlip optional; when set, only assets whose most recent flip on this
 *                         timeframe occurred within this many days match
 */
public record ScanCondition(
        Timeframe timeframe,
        TrendState state,
        Integer maxDaysSinceFlip
) {
}
