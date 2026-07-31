package walshe.projectcolumbo.supertrend.indicator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pure, stateless SuperTrend calculator: True Range -&gt; Wilder ATR -&gt; basic/final bands -&gt; direction/flip.
 * All arithmetic uses {@link BigDecimal} at a fixed scale with HALF_UP rounding for determinism.
 */
public final class SuperTrendCalculator {

    public static final int DEFAULT_ATR_LENGTH = 10;
    public static final BigDecimal DEFAULT_MULTIPLIER = new BigDecimal("2.0");

    private static final int SCALE = 10;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /**
     * Computes SuperTrend for every candle in {@code candles}. Candles before the ATR warm-up
     * period (fewer than {@code atrLength} preceding candles, inclusive) have no result yet and
     * are represented as {@link Optional#empty()} at the corresponding position.
     *
     * @param candles ordered oldest-to-newest, all for one asset/timeframe
     */
    public List<Optional<SuperTrendResult>> calculate(List<Candle> candles, int atrLength, BigDecimal multiplier) {
        List<Optional<SuperTrendResult>> results = new ArrayList<>(candles.size());
        if (candles.isEmpty()) {
            return results;
        }

        BigDecimal[] trueRanges = computeTrueRanges(candles);
        BigDecimal[] atrValues = computeAtr(trueRanges, atrLength);

        BigDecimal prevFinalUpper = null;
        BigDecimal prevFinalLower = null;
        BigDecimal prevSuperTrend = null;
        BigDecimal prevClose = null;

        for (int i = 0; i < candles.size(); i++) {
            Candle candle = candles.get(i);
            BigDecimal atr = atrValues[i];

            if (atr == null) {
                results.add(Optional.empty());
                prevClose = candle.close();
                continue;
            }

            BigDecimal middle = candle.high().add(candle.low()).divide(BigDecimal.valueOf(2), SCALE, ROUNDING);
            BigDecimal basicUpper = middle.add(multiplier.multiply(atr));
            BigDecimal basicLower = middle.subtract(multiplier.multiply(atr));

            BigDecimal finalUpper = computeFinalUpperBand(basicUpper, prevFinalUpper, prevClose);
            BigDecimal finalLower = computeFinalLowerBand(basicLower, prevFinalLower, prevClose);
            BigDecimal superTrend = computeSuperTrendValue(candle.close(), finalUpper, finalLower, prevSuperTrend, prevFinalUpper);
            SuperTrendDirection direction = superTrend.equals(finalLower) ? SuperTrendDirection.UP : SuperTrendDirection.DOWN;

            results.add(Optional.of(new SuperTrendResult(candle.closeTime(), atr, finalUpper, finalLower, superTrend, direction)));

            prevFinalUpper = finalUpper;
            prevFinalLower = finalLower;
            prevSuperTrend = superTrend;
            prevClose = candle.close();
        }

        return results;
    }

    /**
     * Recomputes only the candles needed to bring stored SuperTrend values up to date.
     * <p>
     * When {@code fullRecalc} is true, or no {@code lastStoredCloseTime} exists yet, every
     * candle is recomputed. Otherwise, only candles strictly after {@code lastStoredCloseTime}
     * are returned, computed over a warm-up window of {@code atrLength * 10} preceding candles
     * so Wilder's ATR has time to restabilize before the first returned value.
     * <p>
     * Unlike {@link #calculate}, this method never returns a placeholder for a still-warming-up
     * candle — callers here only ever want concrete, persistable results.
     */
    public List<SuperTrendResult> calculateIncremental(
            List<Candle> candles,
            int atrLength,
            BigDecimal multiplier,
            OffsetDateTime lastStoredCloseTime,
            boolean fullRecalc
    ) {
        if (fullRecalc || lastStoredCloseTime == null) {
            return concreteResultsAfter(calculate(candles, atrLength, multiplier), null);
        }
        if (candles.isEmpty()) {
            return List.of();
        }

        int anchorIndex = findAnchorIndex(candles, lastStoredCloseTime);
        if (anchorIndex == -1) {
            return List.of();
        }

        int windowStart = Math.max(0, anchorIndex - (atrLength * 10));
        List<Candle> window = candles.subList(windowStart, candles.size());
        return concreteResultsAfter(calculate(window, atrLength, multiplier), lastStoredCloseTime);
    }

    private static int findAnchorIndex(List<Candle> candles, OffsetDateTime lastStoredCloseTime) {
        for (int i = 0; i < candles.size(); i++) {
            OffsetDateTime closeTime = candles.get(i).closeTime();
            if (!closeTime.isBefore(lastStoredCloseTime)) {
                return i;
            }
        }
        return -1;
    }

    private static List<SuperTrendResult> concreteResultsAfter(List<Optional<SuperTrendResult>> results, OffsetDateTime exclusiveLowerBound) {
        List<SuperTrendResult> out = new ArrayList<>();
        for (Optional<SuperTrendResult> result : results) {
            if (result.isEmpty()) {
                continue;
            }
            SuperTrendResult value = result.get();
            if (exclusiveLowerBound == null || value.closeTime().isAfter(exclusiveLowerBound)) {
                out.add(value);
            }
        }
        return out;
    }

    private static BigDecimal computeFinalUpperBand(BigDecimal basicUpper, BigDecimal prevFinalUpper, BigDecimal prevClose) {
        if (prevFinalUpper == null || basicUpper.compareTo(prevFinalUpper) < 0 || prevClose.compareTo(prevFinalUpper) > 0) {
            return basicUpper;
        }
        return prevFinalUpper;
    }

    private static BigDecimal computeFinalLowerBand(BigDecimal basicLower, BigDecimal prevFinalLower, BigDecimal prevClose) {
        if (prevFinalLower == null || basicLower.compareTo(prevFinalLower) > 0 || prevClose.compareTo(prevFinalLower) < 0) {
            return basicLower;
        }
        return prevFinalLower;
    }

    private static BigDecimal computeSuperTrendValue(
            BigDecimal close,
            BigDecimal finalUpper,
            BigDecimal finalLower,
            BigDecimal prevSuperTrend,
            BigDecimal prevFinalUpper
    ) {
        if (prevSuperTrend == null) {
            // No prior trend to continue: default to the lower band (uptrend) unless price is
            // already below it, matching TradingView's default-to-uptrend behavior.
            return close.compareTo(finalLower) < 0 ? finalUpper : finalLower;
        }
        if (prevSuperTrend.equals(prevFinalUpper)) {
            return close.compareTo(finalUpper) <= 0 ? finalUpper : finalLower;
        }
        // prevSuperTrend was on the lower band
        return close.compareTo(finalLower) >= 0 ? finalLower : finalUpper;
    }

    private static BigDecimal[] computeTrueRanges(List<Candle> candles) {
        BigDecimal[] trueRanges = new BigDecimal[candles.size()];
        BigDecimal prevClose = null;

        for (int i = 0; i < candles.size(); i++) {
            Candle candle = candles.get(i);
            BigDecimal highMinusLow = candle.high().subtract(candle.low());

            if (prevClose == null) {
                trueRanges[i] = highMinusLow;
            } else {
                BigDecimal highMinusPrevClose = candle.high().subtract(prevClose).abs();
                BigDecimal lowMinusPrevClose = candle.low().subtract(prevClose).abs();
                trueRanges[i] = highMinusLow.max(highMinusPrevClose).max(lowMinusPrevClose);
            }
            prevClose = candle.close();
        }
        return trueRanges;
    }

    private static BigDecimal[] computeAtr(BigDecimal[] trueRanges, int atrLength) {
        BigDecimal[] atr = new BigDecimal[trueRanges.length];
        if (trueRanges.length < atrLength) {
            return atr;
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < atrLength; i++) {
            sum = sum.add(trueRanges[i]);
        }
        atr[atrLength - 1] = sum.divide(BigDecimal.valueOf(atrLength), SCALE, ROUNDING);

        BigDecimal nMinusOne = BigDecimal.valueOf(atrLength - 1);
        BigDecimal nDivisor = BigDecimal.valueOf(atrLength);
        for (int i = atrLength; i < trueRanges.length; i++) {
            atr[i] = atr[i - 1].multiply(nMinusOne).add(trueRanges[i]).divide(nDivisor, SCALE, ROUNDING);
        }
        return atr;
    }
}
