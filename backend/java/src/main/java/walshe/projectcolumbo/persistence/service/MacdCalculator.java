package walshe.projectcolumbo.persistence.service;

import walshe.projectcolumbo.persistence.entity.Candle;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculator for MACD (Moving Average Convergence Divergence) with standard parameters 12-26-9.
 *
 * Depends on EmaCalculator for all three EMA computations (fast, slow, signal).
 * Minimum candles required for first result: SLOW_PERIOD + SIGNAL_PERIOD - 1 = 34.
 */
@Component
public class MacdCalculator {

    static final int FAST_PERIOD = 12;
    static final int SLOW_PERIOD = 26;
    static final int SIGNAL_PERIOD = 9;

    private final EmaCalculator emaCalculator;

    public MacdCalculator(EmaCalculator emaCalculator) {
        this.emaCalculator = emaCalculator;
    }

    public record MacdResult(OffsetDateTime closeTime, BigDecimal macdLine, BigDecimal signalLine, BigDecimal histogram) {}

    /**
     * Computes MACD 12-26-9 for a candle series.
     * Requires at least SLOW_PERIOD + SIGNAL_PERIOD - 1 = 34 candles for the first result.
     *
     * Algorithm:
     *   1. Fast EMA(12) and slow EMA(26) from close prices
     *   2. MACD line = fast EMA - slow EMA (common close times only)
     *   3. Signal line = EMA(9) of MACD line
     *   4. Histogram = MACD line - signal line
     *
     * @param candles sorted by close time ascending
     * @return List of MacdResult — one per candle where histogram is computable
     */
    public List<MacdResult> calculate(List<Candle> candles) {
        int minCandles = SLOW_PERIOD + SIGNAL_PERIOD - 1;
        if (candles == null || candles.size() < minCandles) {
            return List.of();
        }

        // Step 1: Fast and slow EMAs over the full candle series
        List<EmaCalculator.EmaResult> fastEma = emaCalculator.calculate(candles, FAST_PERIOD);
        List<EmaCalculator.EmaResult> slowEma = emaCalculator.calculate(candles, SLOW_PERIOD);

        // Step 2: MACD line = fast - slow, aligned by close time
        // Build fast EMA lookup by time
        Map<OffsetDateTime, BigDecimal> fastEmaByTime = new HashMap<>();
        for (EmaCalculator.EmaResult r : fastEma) {
            fastEmaByTime.put(r.closeTime(), r.emaValue());
        }

        List<BigDecimal> macdLineValues = new ArrayList<>();
        List<OffsetDateTime> macdLineTimes = new ArrayList<>();
        for (EmaCalculator.EmaResult slow : slowEma) {
            BigDecimal fast = fastEmaByTime.get(slow.closeTime());
            if (fast != null) {
                macdLineValues.add(fast.subtract(slow.emaValue()));
                macdLineTimes.add(slow.closeTime());
            }
        }

        // Step 3: Signal line = EMA(9) of MACD line values
        List<EmaCalculator.EmaResult> signalEma = emaCalculator.calculateFromValues(
                macdLineValues, macdLineTimes, SIGNAL_PERIOD);

        // Step 4: Histogram = MACD line - signal line
        // Build MACD line lookup for the close times where signal line is defined
        Map<OffsetDateTime, BigDecimal> macdLineByTime = new HashMap<>();
        for (int i = 0; i < macdLineTimes.size(); i++) {
            macdLineByTime.put(macdLineTimes.get(i), macdLineValues.get(i));
        }

        List<MacdResult> results = new ArrayList<>();
        for (EmaCalculator.EmaResult signal : signalEma) {
            BigDecimal macdLine = macdLineByTime.get(signal.closeTime());
            BigDecimal histogram = macdLine.subtract(signal.emaValue());
            results.add(new MacdResult(signal.closeTime(), macdLine, signal.emaValue(), histogram));
        }

        return results;
    }
}
