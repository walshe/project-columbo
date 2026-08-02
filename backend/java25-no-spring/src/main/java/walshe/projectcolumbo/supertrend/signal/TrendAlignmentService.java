package walshe.projectcolumbo.supertrend.signal;

import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cross-timeframe (W1 + D1) SuperTrend confluence and retest computation. A confluence entry
 * requires the exact same trend state on both timeframes simultaneously - an asset with
 * {@code UNKNOWN} on either timeframe (e.g. still in its ATR warm-up window) is silently excluded
 * from every list, not specially handled. A retest entry is the mirror-image counter-trend case:
 * the W1 trend is intact but D1 has recently flipped against it, within {@code maxRetestAgeDays}.
 */
public final class TrendAlignmentService {

    private final SignalQueryService signalQueryService;
    private final Clock clock;

    public TrendAlignmentService(SignalQueryService signalQueryService, Clock clock) {
        this.signalQueryService = signalQueryService;
        this.clock = clock;
    }

    public TrendAlignment computeAlignment(int maxRetestAgeDays) {
        // One unfiltered fetch per timeframe rather than one per (timeframe, state) pair - listSignals'
        // state filter is applied in-memory downstream of its DAO calls anyway, so fetching once per
        // timeframe and splitting by trend state here avoids re-running the same ~6 queries twice per
        // timeframe. Filtering a list preserves relative order, so splitting the D1 list (already
        // fetched sorted last-flip-descending) still leaves each half correctly ordered.
        List<SignalSummary> w1All = signalQueryService.listSignals(Timeframe.W1, null, null);
        List<SignalSummary> d1AllByFlipDesc = signalQueryService.listSignals(Timeframe.D1, null, SignalSort.LAST_FLIP_DESC);

        List<SignalSummary> w1Bullish = byTrendState(w1All, TrendState.BULLISH);
        List<SignalSummary> w1Bearish = byTrendState(w1All, TrendState.BEARISH);
        List<SignalSummary> d1Bullish = byTrendState(d1AllByFlipDesc, TrendState.BULLISH);
        List<SignalSummary> d1Bearish = byTrendState(d1AllByFlipDesc, TrendState.BEARISH);

        return align(w1Bullish, w1Bearish, d1Bullish, d1Bearish, maxRetestAgeDays, OffsetDateTime.now(clock));
    }

    private static List<SignalSummary> byTrendState(List<SignalSummary> summaries, TrendState trendState) {
        return summaries.stream().filter(entry -> entry.trendState() == trendState).toList();
    }

    /** Pure: intersection, retest-age filtering, testable without a database. */
    static TrendAlignment align(
            List<SignalSummary> w1Bullish, List<SignalSummary> w1Bearish,
            List<SignalSummary> d1Bullish, List<SignalSummary> d1Bearish,
            int maxRetestAgeDays, OffsetDateTime now) {

        List<SignalSummary> bullishConfluence = intersectBySymbol(d1Bullish, w1Bullish);
        List<SignalSummary> bullishRetest = retest(d1Bearish, w1Bullish, maxRetestAgeDays, now);
        List<SignalSummary> bearishConfluence = intersectBySymbol(d1Bearish, w1Bearish);
        List<SignalSummary> bearishRetest = retest(d1Bullish, w1Bearish, maxRetestAgeDays, now);

        return new TrendAlignment(bullishConfluence, bullishRetest, bearishConfluence, bearishRetest);
    }

    private static List<SignalSummary> intersectBySymbol(List<SignalSummary> d1, List<SignalSummary> w1) {
        Set<String> w1Symbols = w1.stream().map(SignalSummary::symbol).collect(Collectors.toSet());
        return d1.stream().filter(entry -> w1Symbols.contains(entry.symbol())).toList();
    }

    private static List<SignalSummary> retest(List<SignalSummary> d1CounterTrend, List<SignalSummary> w1Aligned, int maxRetestAgeDays, OffsetDateTime now) {
        Set<String> w1Symbols = w1Aligned.stream().map(SignalSummary::symbol).collect(Collectors.toSet());
        return d1CounterTrend.stream()
                .filter(entry -> w1Symbols.contains(entry.symbol()))
                .filter(entry -> {
                    Long days = daysSinceFlip(entry, now);
                    return days != null && days <= maxRetestAgeDays;
                })
                .toList();
    }

    static Long daysSinceFlip(SignalSummary summary, OffsetDateTime now) {
        return summary.lastFlipTime() != null ? ChronoUnit.DAYS.between(summary.lastFlipTime().toLocalDate(), now.toLocalDate()) : null;
    }
}
