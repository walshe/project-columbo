package walshe.projectcolumbo.supertrend.signal;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A provisional (not yet committed) W1 trend read: what the direction and flip-level price would
 * be if the current week closed right now, based on this week's D1 candles ingested so far.
 *
 * @param direction still fundamentally BULLISH/BEARISH - "provisional" describes how much to trust it, not a new direction
 * @param flipLevel the price the week-to-date close would need to cross to flip this reading
 */
public record ProvisionalTrendResult(TrendState direction, BigDecimal flipLevel) {
    public ProvisionalTrendResult {
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(flipLevel, "flipLevel must not be null");
    }
}
