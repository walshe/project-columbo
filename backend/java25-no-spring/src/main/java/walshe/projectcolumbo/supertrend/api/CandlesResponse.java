package walshe.projectcolumbo.supertrend.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import walshe.projectcolumbo.supertrend.indicator.SuperTrendDirection;
import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.SignalEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * One asset's candle time series for {@code GET /api/v1/candles}, shaped so any client-side
 * charting library can plot it directly: every {@code time} is the bar's open time in UTC epoch
 * seconds, and every overlay point shares the {@code time} of the candle it belongs to.
 *
 * @param hasMore        true when older candles exist inside the requested window before {@code candles[0]}
 * @param supertrend     per-candle SuperTrend line - only present with {@code include=supertrend}
 * @param signals        trend flips only (never NONE) - only present with {@code include=supertrend}
 * @param lastIngestionAt finished-at of the last successful ingestion run; null if none has ever completed
 * @param candlesThrough close time of the most recent stored candle for the timeframe (across all assets); null if none
 * @param stale          true if the timeframe's candle data hasn't reached its expected latest close time
 */
public record CandlesResponse(
        String symbol,
        String name,
        AssetClass assetClass,
        Timeframe timeframe,
        String tradingviewUrl,
        boolean hasMore,
        List<Bar> candles,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<SuperTrendPoint> supertrend,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<SignalMarker> signals,
        OffsetDateTime lastIngestionAt,
        OffsetDateTime candlesThrough,
        boolean stale
) {
    public CandlesResponse {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(assetClass, "assetClass must not be null");
        Objects.requireNonNull(timeframe, "timeframe must not be null");
        Objects.requireNonNull(candles, "candles must not be null");
    }

    /** One OHLCV bar; {@code time} is its open time in UTC epoch seconds. */
    public record Bar(long time, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {
    }

    /** SuperTrend line value at the bar with this {@code time}; UP = line below price (bullish), DOWN = above (bearish). */
    public record SuperTrendPoint(long time, BigDecimal value, SuperTrendDirection direction) {
    }

    /** A trend flip (BULLISH_REVERSAL or BEARISH_REVERSAL) on the bar with this {@code time}. */
    public record SignalMarker(long time, SignalEvent event) {
    }
}
