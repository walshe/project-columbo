package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.signal.ProvisionalTrendResult;

import java.util.Objects;

/**
 * An asset not currently confluence-eligible by committed W1+D1 data, but whose provisional W1
 * read now agrees with its committed D1 state - a preview of what might join next week's
 * confirmed confluence list, shown separately from it in {@link WeeklyTrendBriefingFormatter}.
 */
public record FlipForming(String symbol, ProvisionalTrendResult provisional, String tradingviewUrl) {
    public FlipForming {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(provisional, "provisional must not be null");
    }
}
