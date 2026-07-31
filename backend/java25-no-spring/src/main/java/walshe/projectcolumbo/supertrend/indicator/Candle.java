package walshe.projectcolumbo.supertrend.indicator;

import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

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
}
