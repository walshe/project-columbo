package walshe.projectcolumbo.persistence.service;
import walshe.projectcolumbo.persistence.model.SuperTrendResult;
import walshe.projectcolumbo.persistence.model.SuperTrendDirection;
import walshe.projectcolumbo.persistence.entity.Candle;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure, stateless calculator for SuperTrend indicator.
 * Logic based on standard TradingView / SuperTrend implementation.
 */
@Component
public class SuperTrendCalculator {

    private static final int SCALE = 10;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /**
     * Calculates SuperTrend for a list of candles.
     *
     * @param candles    Ordered list of candles (oldest to newest)
     * @param atrLength  ATR smoothing period (e.g., 10)
     * @param multiplier ATR multiplier (e.g., 3.0)
     * @return List of results, matching candle count (null values for initial period before ATR)
     */
    public List<SuperTrendResult> calculate(List<Candle> candles, int atrLength, BigDecimal multiplier) {
        List<SuperTrendResult> results = new ArrayList<>(candles.size());
        if (candles.isEmpty()) {
            return results;
        }

        BigDecimal[] tr = calculateTrueRanges(candles);
        BigDecimal[] atr = calculateATR(tr, atrLength);

        BigDecimal prevFinalUpper = null;
        BigDecimal prevFinalLower = null;
        BigDecimal prevSuperTrend = null;
        BigDecimal prevClose = null;

        for (int i = 0; i < candles.size(); i++) {
            Candle candle = candles.get(i);
            BigDecimal currentAtr = atr[i];

            if (currentAtr == null) {
                results.add(null);
                prevClose = candle.getClose();
                continue;
            }

            // 3. Basic Bands
            BigDecimal high = candle.getHigh();
            BigDecimal low = candle.getLow();
            BigDecimal close = candle.getClose();
            BigDecimal middle = high.add(low).divide(BigDecimal.valueOf(2), SCALE, ROUNDING);

            BigDecimal basicUpper = middle.add(multiplier.multiply(currentAtr));
            BigDecimal basicLower = middle.subtract(multiplier.multiply(currentAtr));

            // 4. Final Bands
            BigDecimal finalUpper;
            if (prevFinalUpper == null || basicUpper.compareTo(prevFinalUpper) < 0 || prevClose.compareTo(prevFinalUpper) > 0) {
                finalUpper = basicUpper;
            } else {
                finalUpper = prevFinalUpper;
            }

            BigDecimal finalLower;
            if (prevFinalLower == null || basicLower.compareTo(prevFinalLower) > 0 || prevClose.compareTo(prevFinalLower) < 0) {
                finalLower = basicLower;
            } else {
                finalLower = prevFinalLower;
            }

            // 5. SuperTrend
            BigDecimal superTrend;
            if (prevSuperTrend == null) {
                // Initial direction - assume DOWN if close <= finalUpper, else UP
                if (close.compareTo(finalUpper) <= 0) {
                    superTrend = finalUpper;
                } else {
                    superTrend = finalLower;
                }
            } else if (prevSuperTrend.equals(prevFinalUpper)) {
                if (close.compareTo(finalUpper) <= 0) {
                    superTrend = finalUpper;
                } else {
                    superTrend = finalLower;
                }
            } else { // prevSuperTrend == prevFinalLower
                if (close.compareTo(finalLower) >= 0) {
                    superTrend = finalLower;
                } else {
                    superTrend = finalUpper;
                }
            }

            // 6. Direction
            SuperTrendDirection direction = superTrend.equals(finalLower) ? SuperTrendDirection.UP : SuperTrendDirection.DOWN;

            results.add(new SuperTrendResult(
                    candle.getCloseTime(),
                    currentAtr,
                    finalUpper,
                    finalLower,
                    superTrend,
                    direction
            ));

            prevFinalUpper = finalUpper;
            prevFinalLower = finalLower;
            prevSuperTrend = superTrend;
            prevClose = close;
        }

        return results;
    }

    /**
     * Incremental calculation helper. Recomputes from a warm-up window around the last stored close time
     * to preserve ATR continuity and returns only the new results after the anchor.
     *
     * @param candles ordered list (oldest to newest)
     * @param atrLength ATR period
     * @param multiplier ATR multiplier
     * @param lastStoredCloseTime null if no prior data
     * @param fullRecalc when true, ignores incremental anchor and returns full results
     * @return list of new results strictly after {@code lastStoredCloseTime}; full list when {@code fullRecalc}
     */
    public List<SuperTrendResult> calculateIncremental(
            List<Candle> candles,
            int atrLength,
            BigDecimal multiplier,
            java.time.OffsetDateTime lastStoredCloseTime,
            boolean fullRecalc
    ) {
        if (fullRecalc || lastStoredCloseTime == null) {
            return calculate(candles, atrLength, multiplier);
        }
        if (candles.isEmpty()) {
            return List.of();
        }

        // Find anchor index: exact match, else first greater; if none greater → up to date
        int anchorIndex = -1;
        for (int i = 0; i < candles.size(); i++) {
            var ct = candles.get(i).getCloseTime();
            if (ct.equals(lastStoredCloseTime)) {
                anchorIndex = i;
                break;
            }
            if (ct.isAfter(lastStoredCloseTime)) {
                anchorIndex = i; // first greater
                break;
            }
        }
        if (anchorIndex == -1) {
            return List.of(); // already up to date
        }

        // Start from a warm-up window before the anchor point.
        // We use atrLength * 10 to ensure the ATR (Wilder's smoothing) has enough history to stabilize.
        int start = Math.max(0, anchorIndex - (atrLength * 10));
        List<Candle> window = candles.subList(start, candles.size());
        List<SuperTrendResult> windowResults = calculate(window, atrLength, multiplier);

        // Return only results with closeTime strictly after lastStoredCloseTime
        List<SuperTrendResult> out = new java.util.ArrayList<>();
        for (SuperTrendResult r : windowResults) {
            if (r == null) continue;
            if (r.closeTime().isAfter(lastStoredCloseTime)) {
                out.add(r);
            }
        }
        return out;
    }

    private BigDecimal[] calculateTrueRanges(List<Candle> candles) {
        BigDecimal[] tr = new BigDecimal[candles.size()];
        BigDecimal prevClose = null;

        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            BigDecimal highMinusLow = c.getHigh().subtract(c.getLow());

            if (prevClose == null) {
                tr[i] = highMinusLow;
            } else {
                BigDecimal highMinusPrevClose = c.getHigh().subtract(prevClose).abs();
                BigDecimal lowMinusPrevClose = c.getLow().subtract(prevClose).abs();
                tr[i] = highMinusLow.max(highMinusPrevClose).max(lowMinusPrevClose);
            }
            prevClose = c.getClose();
        }
        return tr;
    }

    private BigDecimal[] calculateATR(BigDecimal[] tr, int n) {
        BigDecimal[] atr = new BigDecimal[tr.length];
        if (tr.length < n) {
            return atr;
        }

        // First ATR = average of first n True Ranges
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            sum = sum.add(tr[i]);
        }
        atr[n - 1] = sum.divide(BigDecimal.valueOf(n), SCALE, ROUNDING);

        // Subsequent ATR = (previousATR * (n - 1) + currentTR) / n
        BigDecimal nMinusOne = BigDecimal.valueOf(n - 1);
        BigDecimal nDivisor = BigDecimal.valueOf(n);

        for (int i = n; i < tr.length; i++) {
            BigDecimal prevAtr = atr[i - 1];
            atr[i] = prevAtr.multiply(nMinusOne)
                    .add(tr[i])
                    .divide(nDivisor, SCALE, ROUNDING);
        }
        return atr;
    }
}
