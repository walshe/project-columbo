package walshe.projectcolumbo.supertrend.rollup;

import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Pure Monday-start week grouping/aggregation of D1 candles into W1 OHLCV shape - shared by
 * {@link CandleRollupService} (only complete 7-candle weeks) and
 * {@link walshe.projectcolumbo.supertrend.signal.ProvisionalTrendService} (a partial,
 * still-in-progress week), so there is exactly one implementation of "how a week aggregates."
 */
public final class WeeklyCandleAggregation {

    private WeeklyCandleAggregation() {
    }

    /** Groups candles into Monday-start weeks, keyed by that week's Monday (UTC midnight), oldest week first. */
    public static Map<OffsetDateTime, List<Candle>> groupByWeek(List<Candle> candles) {
        return candles.stream().collect(Collectors.groupingBy(
                candle -> candle.openTime()
                        .withOffsetSameInstant(ZoneOffset.UTC)
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .truncatedTo(ChronoUnit.DAYS),
                TreeMap::new,
                Collectors.toList()
        ));
    }

    /** Aggregates one week's (complete or partial) D1 candles into a single W1-shaped OHLCV candle, oldest-to-newest input. */
    public static Candle aggregate(List<Candle> weekCandles) {
        Candle first = weekCandles.get(0);
        Candle last = weekCandles.get(weekCandles.size() - 1);

        BigDecimal high = weekCandles.stream().map(Candle::high).max(BigDecimal::compareTo).orElse(first.high());
        BigDecimal low = weekCandles.stream().map(Candle::low).min(BigDecimal::compareTo).orElse(first.low());
        BigDecimal volume = weekCandles.stream().map(Candle::volume).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Candle(first.openTime(), last.closeTime(), Timeframe.W1, first.open(), high, low, last.close(), volume);
    }
}
