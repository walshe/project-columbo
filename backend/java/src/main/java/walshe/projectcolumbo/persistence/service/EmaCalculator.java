package walshe.projectcolumbo.persistence.service;

import walshe.projectcolumbo.persistence.entity.Candle;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculator for Exponential Moving Average (EMA).
 *
 * Formula: EMA_n = close_n * k + EMA_(n-1) * (1 - k), where k = 2 / (period + 1).
 * Seed value: SMA of the first {@code period} closes.
 *
 * Provides two overloads:
 *   - calculate(List&lt;Candle&gt;, int): extracts close prices from candles
 *   - calculateFromValues(List&lt;BigDecimal&gt;, List&lt;OffsetDateTime&gt;, int): for arbitrary value series
 *     (used by MacdCalculator for the MACD signal line)
 */
@Component
public class EmaCalculator {

    public record EmaResult(OffsetDateTime closeTime, BigDecimal emaValue) {}

    /**
     * Computes EMA for a list of candles.
     *
     * @param candles List of candles sorted by close time ascending.
     * @param period  The EMA period.
     * @return List of EmaResult, or empty if insufficient data.
     */
    public List<EmaResult> calculate(List<Candle> candles, int period) {
        if (candles == null || candles.size() < period) {
            return List.of();
        }
        List<BigDecimal> closes = candles.stream().map(Candle::getClose).toList();
        List<OffsetDateTime> times = candles.stream().map(Candle::getCloseTime).toList();
        return calculateFromValues(closes, times, period);
    }

    /**
     * Computes EMA from arbitrary value series. Used by MacdCalculator for the signal line.
     *
     * @param values     List of values sorted ascending.
     * @param closeTimes Corresponding close times (same size as values).
     * @param period     The EMA period.
     * @return List of EmaResult, or empty if insufficient data.
     */
    public List<EmaResult> calculateFromValues(List<BigDecimal> values, List<OffsetDateTime> closeTimes, int period) {
        if (values == null || values.size() < period) {
            return List.of();
        }

        List<EmaResult> results = new ArrayList<>();

        // First value: SMA of first `period` closes (assigned to close time at index period-1)
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(values.get(i));
        }
        BigDecimal ema = sum.divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP);
        results.add(new EmaResult(closeTimes.get(period - 1), ema));

        // Multiplier k = 2 / (period + 1)
        BigDecimal k = BigDecimal.valueOf(2)
                .divide(BigDecimal.valueOf(period + 1), 10, RoundingMode.HALF_UP);
        BigDecimal oneMinusK = BigDecimal.ONE.subtract(k);

        // Subsequent values: EMA = close * k + prevEma * (1 - k)
        for (int i = period; i < values.size(); i++) {
            ema = values.get(i).multiply(k)
                    .add(ema.multiply(oneMinusK))
                    .setScale(10, RoundingMode.HALF_UP);
            results.add(new EmaResult(closeTimes.get(i), ema));
        }

        return results;
    }
}
