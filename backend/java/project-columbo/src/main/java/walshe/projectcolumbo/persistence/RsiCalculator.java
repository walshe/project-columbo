package walshe.projectcolumbo.persistence;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculator for Relative Strength Index (RSI) using Wilder's smoothing method.
 */
@Component
public class RsiCalculator {

    public record RsiResult(OffsetDateTime closeTime, BigDecimal rsiValue) {}

    /**
     * Computes RSI for a list of candles.
     * To get a valid 14-period RSI, at least 15 candles are required (14 changes + 1 initial).
     *
     * @param candles List of candles sorted by close time ascending.
     * @param period  The RSI period (typically 14).
     * @return List of RsiResult.
     */
    public List<RsiResult> calculate(List<Candle> candles, int period) {
        if (candles == null || candles.size() <= period) {
            return List.of();
        }

        List<RsiResult> results = new ArrayList<>();
        List<BigDecimal> closes = candles.stream().map(Candle::getClose).toList();

        BigDecimal avgGain = BigDecimal.ZERO;
        BigDecimal avgLoss = BigDecimal.ZERO;

        // 1. Initial SMA-like average for the first 'period'
        for (int i = 1; i <= period; i++) {
            BigDecimal change = closes.get(i).subtract(closes.get(i - 1));
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                avgGain = avgGain.add(change);
            } else {
                avgLoss = avgLoss.add(change.abs());
            }
        }

        BigDecimal periodBD = BigDecimal.valueOf(period);
        avgGain = avgGain.divide(periodBD, 10, RoundingMode.HALF_UP);
        avgLoss = avgLoss.divide(periodBD, 10, RoundingMode.HALF_UP);

        // First RSI value
        results.add(new RsiResult(candles.get(period).getCloseTime(), calculateRsiValue(avgGain, avgLoss)));

        // 2. Wilder's Smoothing for subsequent values
        for (int i = period + 1; i < candles.size(); i++) {
            BigDecimal change = closes.get(i).subtract(closes.get(i - 1));
            BigDecimal currentGain = change.compareTo(BigDecimal.ZERO) > 0 ? change : BigDecimal.ZERO;
            BigDecimal currentLoss = change.compareTo(BigDecimal.ZERO) < 0 ? change.abs() : BigDecimal.ZERO;

            // Wilder's formula: Smoothed Avg = ((Prev Avg * (period - 1)) + Current) / period
            avgGain = avgGain.multiply(periodBD.subtract(BigDecimal.ONE))
                    .add(currentGain)
                    .divide(periodBD, 10, RoundingMode.HALF_UP);

            avgLoss = avgLoss.multiply(periodBD.subtract(BigDecimal.ONE))
                    .add(currentLoss)
                    .divide(periodBD, 10, RoundingMode.HALF_UP);

            results.add(new RsiResult(candles.get(i).getCloseTime(), calculateRsiValue(avgGain, avgLoss)));
        }

        return results;
    }

    private BigDecimal calculateRsiValue(BigDecimal avgGain, BigDecimal avgLoss) {
        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100); // down == 0 ? 100
        }

        if (avgGain.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO; // up == 0 ? 0
        }

        BigDecimal rs = avgGain.divide(avgLoss, 10, RoundingMode.HALF_UP);
        // RSI = 100 - (100 / (1 + RS))
        return BigDecimal.valueOf(100).subtract(
                BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(rs), 4, RoundingMode.HALF_UP)
        );
    }
}
