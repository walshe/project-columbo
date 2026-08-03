package walshe.projectcolumbo.supertrend.shared;

import java.time.Duration;
import java.time.OffsetDateTime;

public enum Timeframe {
    D1(Duration.ofDays(1)),
    W1(Duration.ofDays(7));

    private final Duration candleSpan;

    Timeframe(Duration candleSpan) {
        this.candleSpan = candleSpan;
    }

    /**
     * Open time of the candle that closes at {@code closeTime} on this timeframe. Binance klines
     * (and our own W1 rollup, which derives its close time from real D1 candles) always satisfy
     * {@code close = open + span - 1ms}, so this is an exact reverse-derivation, not an estimate -
     * charting tools like TradingView position/label a candle by its open time, so this lets
     * anything timestamped by a candle's close (e.g. a signal flip) be reported the same way a
     * chart would show it.
     */
    public OffsetDateTime openTimeFor(OffsetDateTime closeTime) {
        return closeTime.minus(candleSpan).plusNanos(1_000_000);
    }
}
