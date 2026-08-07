package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.ScanConditionMatch;
import walshe.projectcolumbo.supertrend.signal.ScanResult;
import walshe.projectcolumbo.supertrend.signal.SignalQueryService;
import walshe.projectcolumbo.supertrend.signal.SignalSummary;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import java.math.BigDecimal;
import java.util.Comparator;

/**
 * Lookups shared by every {@code weekly-*-briefing} handler
 * ({@link WeeklyTrendBriefingHandler}, {@link WeeklyPullbackBriefingHandler}): the BTC
 * direction tiebreaker and reading/ranking a scan match's D1 leg are identical regardless of
 * which trading philosophy a given briefing is built around.
 */
final class WeeklyBriefingSignals {

    private static final String BTC_SYMBOL = "BTCUSDT";

    private WeeklyBriefingSignals() {
    }

    static TrendState btcState(SignalQueryService signalQueryService, Timeframe timeframe) {
        return signalQueryService.listSignals(timeframe, null, null, AssetClass.CRYPTO).stream()
                .filter(signal -> signal.symbol().equals(BTC_SYMBOL))
                .map(SignalSummary::trendState)
                .findFirst()
                .orElse(null);
    }

    /**
     * Ranks by the D1 leg's movement since flip, nulls always last regardless of direction.
     * <p>
     * Deliberately builds the descending case as {@code nullsLast(reverseOrder())} rather than
     * {@code nullsLast(naturalOrder()).reversed()} on the ascending comparator - the latter
     * reverses null placement along with everything else, so nulls-last silently becomes
     * nulls-first. Same pattern as {@code SignalQueryService}'s {@code PCT_CHANGE_DESC}.
     */
    static Comparator<ScanResult> byD1Movement(boolean descending) {
        Comparator<BigDecimal> valueOrder = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return Comparator.comparing(WeeklyBriefingSignals::d1PctChangeSinceFlip, Comparator.nullsLast(valueOrder));
    }

    private static BigDecimal d1PctChangeSinceFlip(ScanResult result) {
        return result.matchedConditions().stream()
                .filter(match -> match.timeframe() == Timeframe.D1)
                .findFirst()
                .map(ScanConditionMatch::pctChangeSinceFlip)
                .orElse(null);
    }
}
