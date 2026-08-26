package walshe.projectcolumbo.supertrend.indicator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pure, stateless SuperTrend calculator: True Range -&gt; Wilder ATR -&gt; up/down bands -&gt; trend
 * direction. Deliberately matches the widely-used ("KivancOzbilgic") reference Pine Script v4
 * SuperTrend indicator bar-for-bar, including its specific trend-flip timing (tested against the
 * <em>previous</em> bar's bands, not the current bar's) - see {@link #computeDirection} for where
 * that distinction matters. All arithmetic uses {@link BigDecimal} at a fixed scale with HALF_UP
 * rounding for determinism.
 */
public final class SuperTrendCalculator {

    public static final int DEFAULT_ATR_LENGTH = 10;
    public static final BigDecimal DEFAULT_MULTIPLIER = new BigDecimal("3.0");

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

        BigDecimal prevUp = null;
        BigDecimal prevDn = null;
        SuperTrendDirection prevDirection = null;
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
            BigDecimal basicUp = middle.subtract(multiplier.multiply(atr));
            BigDecimal basicDn = middle.add(multiplier.multiply(atr));

            BigDecimal up = computeUpBand(basicUp, prevUp, prevClose);
            BigDecimal dn = computeDnBand(basicDn, prevDn, prevClose);
            SuperTrendDirection direction = computeDirection(candle.close(), up, dn, prevUp, prevDn, prevDirection);
            BigDecimal superTrend = direction == SuperTrendDirection.UP ? up : dn;

            results.add(Optional.of(new SuperTrendResult(candle.closeTime(), atr, dn, up, superTrend, direction)));

            prevUp = up;
            prevDn = dn;
            prevDirection = direction;
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

    /**
     * Support ("up") band - matches the reference Pine script's {@code up := close[1] > up1 ?
     * max(up, up1) : up}: ratchets up (never drops) while the previous close held above the
     * previous band, otherwise resets to the freshly computed basic value.
     */
    private static BigDecimal computeUpBand(BigDecimal basicUp, BigDecimal prevUp, BigDecimal prevClose) {
        if (prevUp == null) {
            return basicUp;
        }
        return prevClose.compareTo(prevUp) > 0 ? basicUp.max(prevUp) : basicUp;
    }

    /**
     * Resistance ("dn") band - matches the reference Pine script's {@code dn := close[1] < dn1 ?
     * min(dn, dn1) : dn}: ratchets down (never rises) while the previous close held below the
     * previous band, otherwise resets to the freshly computed basic value.
     */
    private static BigDecimal computeDnBand(BigDecimal basicDn, BigDecimal prevDn, BigDecimal prevClose) {
        if (prevDn == null) {
            return basicDn;
        }
        return prevClose.compareTo(prevDn) < 0 ? basicDn.min(prevDn) : basicDn;
    }

    /**
     * Matches the reference Pine script's {@code trend := trend == -1 and close > dn1 ? 1 :
     * trend == 1 and close < up1 ? -1 : trend}. Deliberately tests {@code close} against the
     * <em>previous</em> bar's bands ({@code up1}/{@code dn1}, Pine's {@code nz(up[1], up)}/
     * {@code nz(dn[1], dn)}) - not this bar's freshly (re)computed {@code up}/{@code dn} - so a
     * band reset and a trend flip on the same bar don't get conflated. Falls back to this bar's
     * own band (self-reference, matching Pine's {@code nz} default) on the very first bar, and to
     * {@code UP} (Pine's {@code trend = 1} literal init) when there's no prior direction.
     */
    private static SuperTrendDirection computeDirection(
            BigDecimal close,
            BigDecimal up,
            BigDecimal dn,
            BigDecimal prevUp,
            BigDecimal prevDn,
            SuperTrendDirection prevDirection
    ) {
        BigDecimal up1 = prevUp != null ? prevUp : up;
        BigDecimal dn1 = prevDn != null ? prevDn : dn;
        SuperTrendDirection currentDirection = prevDirection != null ? prevDirection : SuperTrendDirection.UP;

        if (currentDirection == SuperTrendDirection.DOWN && close.compareTo(dn1) > 0) {
            return SuperTrendDirection.UP;
        }
        if (currentDirection == SuperTrendDirection.UP && close.compareTo(up1) < 0) {
            return SuperTrendDirection.DOWN;
        }
        return currentDirection;
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
