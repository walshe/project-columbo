package walshe.projectcolumbo.supertrend.signal;

import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.time.OffsetDateTime;

/**
 * Why a matched asset satisfied one particular {@link ScanCondition} - including a
 * {@code tradingviewUrl} for that condition's own timeframe. Unlike the old app (which picked one
 * "best guess" chart per asset via a hardcoded timeframe-priority ranking), a link is provided per
 * matched condition here - simpler, and more informative when AND-combined conditions span
 * different timeframes (e.g. "D1 bullish AND W1 bullish" surfaces both charts, not just one).
 */
public record ScanConditionMatch(
        Timeframe timeframe,
        TrendState state,
        OffsetDateTime lastFlipTime,
        Long daysSinceFlip,
        String tradingviewUrl
) {
}
