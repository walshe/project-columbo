package walshe.projectcolumbo.supertrend.indicator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

public record SuperTrendResult(
        OffsetDateTime closeTime,
        BigDecimal atr,
        BigDecimal upperBand,
        BigDecimal lowerBand,
        BigDecimal superTrend,
        SuperTrendDirection direction
) {
    public SuperTrendResult {
        Objects.requireNonNull(closeTime, "closeTime must not be null");
        Objects.requireNonNull(atr, "atr must not be null");
        Objects.requireNonNull(upperBand, "upperBand must not be null");
        Objects.requireNonNull(lowerBand, "lowerBand must not be null");
        Objects.requireNonNull(superTrend, "superTrend must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        if (atr.signum() < 0) {
            throw new IllegalArgumentException("atr must not be negative, was: " + atr);
        }
    }
}
