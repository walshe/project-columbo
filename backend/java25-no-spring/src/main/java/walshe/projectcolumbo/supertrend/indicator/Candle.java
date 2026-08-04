package walshe.projectcolumbo.supertrend.indicator;

import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * One OHLCV candle for a single asset/timeframe.
 *
 * @param closeTime the finalized close time this candle represents; the anchor for all
 *                  downstream computation (indicator values, signal events, freshness checks)
 */
public record Candle(
        OffsetDateTime openTime,
        OffsetDateTime closeTime,
        Timeframe timeframe,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume
) {
    public Candle {
        Objects.requireNonNull(openTime, "openTime must not be null");
        Objects.requireNonNull(closeTime, "closeTime must not be null");
        Objects.requireNonNull(timeframe, "timeframe must not be null");
        Objects.requireNonNull(open, "open must not be null");
        Objects.requireNonNull(high, "high must not be null");
        Objects.requireNonNull(low, "low must not be null");
        Objects.requireNonNull(close, "close must not be null");
        Objects.requireNonNull(volume, "volume must not be null");
        if (open.signum() <= 0 || high.signum() <= 0 || low.signum() <= 0 || close.signum() <= 0) {
            throw new IllegalArgumentException("OHLC prices must be positive: open=" + open
                    + " high=" + high + " low=" + low + " close=" + close);
        }
        if (volume.signum() < 0) {
            throw new IllegalArgumentException("volume must not be negative, was: " + volume);
        }
    }
}
