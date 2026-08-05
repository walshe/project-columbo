package walshe.projectcolumbo.supertrend.signal;

import walshe.projectcolumbo.supertrend.shared.AssetClass;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * @param lastFlipTime         open time of the candle whose close triggered the most recent flip
 *                              (event != NONE); null if the asset has never flipped on this
 *                              timeframe. Reported by open time (via {@link
 *                              walshe.projectcolumbo.supertrend.shared.Timeframe#openTimeFor})
 *                              rather than close time so it lines up with how charting tools
 *                              position/label the same candle
 * @param avgVolume7d           rolling 7-day average D1 volume; zero if unavailable
 * @param pctChangeSinceFlip    signed, 2dp; null if there's no flip or no matching candle close
 * @param tradingviewUrl        TradingView chart deep link for this asset+timeframe; null if the
 *                              asset's provider/symbol is unavailable
 * @param assetClass            the asset's category (crypto/stock/etf/commodity)
 */
public record SignalSummary(
        String symbol,
        TrendState trendState,
        OffsetDateTime lastFlipTime,
        BigDecimal avgVolume7d,
        BigDecimal pctChangeSinceFlip,
        String tradingviewUrl,
        AssetClass assetClass
) {
    public SignalSummary {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(trendState, "trendState must not be null");
        Objects.requireNonNull(avgVolume7d, "avgVolume7d must not be null");
        Objects.requireNonNull(assetClass, "assetClass must not be null");
    }

    /** Whole UTC calendar days between {@link #lastFlipTime} and {@code now}; null if there's no recorded flip. */
    public Long daysSinceFlip(OffsetDateTime now) {
        return lastFlipTime != null ? ChronoUnit.DAYS.between(lastFlipTime.toLocalDate(), now.toLocalDate()) : null;
    }
}
