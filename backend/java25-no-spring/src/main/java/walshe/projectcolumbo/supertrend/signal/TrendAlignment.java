package walshe.projectcolumbo.supertrend.signal;

import java.util.List;

/**
 * @param bullishConfluence bullish on both W1 and D1, ordered by D1 flip date descending
 * @param bullishRetest     W1 bullish, D1 recently flipped bearish (within {@code maxRetestAgeDays})
 * @param bearishConfluence bearish on both W1 and D1, ordered by D1 flip date descending
 * @param bearishRetest     W1 bearish, D1 recently flipped bullish (within {@code maxRetestAgeDays})
 */
public record TrendAlignment(
        List<SignalSummary> bullishConfluence,
        List<SignalSummary> bullishRetest,
        List<SignalSummary> bearishConfluence,
        List<SignalSummary> bearishRetest
) {
}
