package walshe.projectcolumbo.supertrend.signal;

import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Why a matched asset satisfied one particular {@link ScanCondition} - including a
 * {@code tradingviewUrl} for that condition's own timeframe. Unlike the old app (which picked one
 * "best guess" chart per asset via a hardcoded timeframe-priority ranking), a link is provided per
 * matched condition here - simpler, and more informative when AND-combined conditions span
 * different timeframes (e.g. "D1 bullish AND W1 bullish" surfaces both charts, not just one).
 *
 * @param pctChangeSinceFlip signed, 2dp; null if there's no flip or no matching candle close - same as {@link SignalSummary#pctChangeSinceFlip()}
 */
public record ScanConditionMatch(
        Timeframe timeframe,
        TrendState state,
        OffsetDateTime lastFlipTime,
        Long daysSinceFlip,
        BigDecimal pctChangeSinceFlip,
        String tradingviewUrl
) {
    public ScanConditionMatch {
        Objects.requireNonNull(timeframe, "timeframe must not be null");
        Objects.requireNonNull(state, "state must not be null");
    }
}
