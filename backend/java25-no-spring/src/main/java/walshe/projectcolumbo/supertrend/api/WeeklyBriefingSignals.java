package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.ScanConditionMatch;
import walshe.projectcolumbo.supertrend.signal.ScanResult;
import walshe.projectcolumbo.supertrend.signal.SignalQueryService;
import walshe.projectcolumbo.supertrend.signal.SignalSummary;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import java.math.BigDecimal;

/**
 * Lookups shared by every {@code weekly-*-briefing} handler
 * ({@link WeeklyTrendBriefingHandler}, {@link WeeklyPullbackBriefingHandler}): the BTC
 * direction tiebreaker and reading a scan match's D1 leg are identical regardless of which
 * trading philosophy a given briefing is built around.
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

    static BigDecimal d1PctChangeSinceFlip(ScanResult result) {
        return result.matchedConditions().stream()
                .filter(match -> match.timeframe() == Timeframe.D1)
                .findFirst()
                .map(ScanConditionMatch::pctChangeSinceFlip)
                .orElse(null);
    }
}
