package walshe.projectcolumbo.supertrend.indicator;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SuperTrendCalculatorTest {

    private static final OffsetDateTime BASE_TIME = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final SuperTrendCalculator CALCULATOR = new SuperTrendCalculator();

    /**
     * Five hand-picked candles, atrLength=2 and multiplier=1 chosen purely so the True
     * Range/ATR/band arithmetic is exact and hand-verifiable (see conversation history for the
     * worked calculation). Candle 3 is where price breaks below the sticky final lower band,
     * producing a flip from UP to DOWN — candle 2 and candle 3 each separately demonstrate one
     * of the two band-stickiness rules holding the final band at its previous value.
     */
    private static List<Candle> fiveCandleScenario() {
        return List.of(
                candle(0, 10, 8, 9),
                candle(1, 11, 9, 10),
                candle(2, 12, 10, 11),
                candle(3, 9, 7, 8),
                candle(4, 8, 6, 7)
        );
    }

    private static Candle candle(int dayOffset, long high, long low, long close) {
        OffsetDateTime closeTime = BASE_TIME.plusDays(dayOffset);
        return new Candle(
                closeTime.minusDays(1),
                closeTime,
                Timeframe.D1,
                BigDecimal.valueOf((high + low) / 2.0),
                BigDecimal.valueOf(high),
                BigDecimal.valueOf(low),
                BigDecimal.valueOf(close),
                BigDecimal.ZERO
        );
    }

    @Test
    void firstCandleBeforeWarmUpHasNoResult() {
        List<Optional<SuperTrendResult>> results = CALCULATOR.calculate(fiveCandleScenario(), 2, BigDecimal.ONE);

        assertThat(results.get(0)).isEmpty();
        assertThat(results.subList(1, 5)).allSatisfy(r -> assertThat(r).isPresent());
    }

    @Test
    void repeatedCalculationOverIdenticalInputIsDeterministic() {
        List<Candle> candles = fiveCandleScenario();

        List<Optional<SuperTrendResult>> first = CALCULATOR.calculate(candles, 2, BigDecimal.ONE);
        List<Optional<SuperTrendResult>> second = CALCULATOR.calculate(candles, 2, BigDecimal.ONE);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void finalUpperBandStaysStickyWhenBasicUpperRisesWithoutABreakout() {
        // Candle index 2: basicUpper (13.0) > prevFinalUpper (12.0), and prevClose (10) did not
        // break above prevFinalUpper (12.0) -> finalUpper must stay at 12.0, not jump to 13.0.
        List<Optional<SuperTrendResult>> results = CALCULATOR.calculate(fiveCandleScenario(), 2, BigDecimal.ONE);

        assertThat(results.get(2)).isPresent();
        assertThat(results.get(2).get().upperBand()).isEqualByComparingTo("12.0");
    }

    @Test
    void finalLowerBandStaysStickyWhenBasicLowerFallsWithoutABreakdown() {
        // Candle index 3: basicLower (5.0) < prevFinalLower (9.0), and prevClose (11) did not
        // break below prevFinalLower (9.0) -> finalLower must stay at 9.0, not drop to 5.0.
        List<Optional<SuperTrendResult>> results = CALCULATOR.calculate(fiveCandleScenario(), 2, BigDecimal.ONE);

        assertThat(results.get(3)).isPresent();
        assertThat(results.get(3).get().lowerBand()).isEqualByComparingTo("9.0");
    }

    @Test
    void flipsFromUpToDownWhenCloseBreaksBelowStickyFinalLowerBand() {
        List<Optional<SuperTrendResult>> results = CALCULATOR.calculate(fiveCandleScenario(), 2, BigDecimal.ONE);

        assertThat(results.get(1).get().direction()).isEqualTo(SuperTrendDirection.UP);
        assertThat(results.get(2).get().direction()).isEqualTo(SuperTrendDirection.UP);
        assertThat(results.get(3).get().direction()).isEqualTo(SuperTrendDirection.DOWN);
        assertThat(results.get(4).get().direction()).isEqualTo(SuperTrendDirection.DOWN);
    }

    @Test
    void incrementalRecomputeMatchesFullRecalcForCandlesAfterTheAnchor() {
        List<Candle> candles = fiveCandleScenario();
        OffsetDateTime anchor = candles.get(2).closeTime();

        List<SuperTrendResult> fullResults = CALCULATOR.calculate(candles, 2, BigDecimal.ONE).stream()
                .flatMap(Optional::stream)
                .toList();
        List<SuperTrendResult> incrementalResults = CALCULATOR.calculateIncremental(candles, 2, BigDecimal.ONE, anchor, false);

        List<SuperTrendResult> expectedAfterAnchor = fullResults.stream()
                .filter(r -> r.closeTime().isAfter(anchor))
                .toList();

        assertThat(incrementalResults).isEqualTo(expectedAfterAnchor);
        assertThat(incrementalResults).hasSize(2); // candle 3 and candle 4
    }

    @Test
    void incrementalRecomputeIgnoresAnchorWhenFullRecalcRequested() {
        List<Candle> candles = fiveCandleScenario();
        OffsetDateTime anchor = candles.get(2).closeTime();

        List<SuperTrendResult> fullRecalcResults = CALCULATOR.calculateIncremental(candles, 2, BigDecimal.ONE, anchor, true);
        List<SuperTrendResult> expected = CALCULATOR.calculate(candles, 2, BigDecimal.ONE).stream()
                .flatMap(Optional::stream)
                .toList();

        assertThat(fullRecalcResults).isEqualTo(expected);
        assertThat(fullRecalcResults).hasSize(4); // every candle except the warm-up candle 0
    }

    /**
     * Regression test for a real bug found comparing this calculator against the reference Pine
     * Script v4 SuperTrend indicator (the widely-used "KivancOzbilgic" version): the trend-flip
     * test must compare {@code close} against the <em>previous</em> bar's band (Pine's {@code
     * up1 = nz(up[1], up)}), not this bar's freshly (re)computed band. The two only disagree when
     * a band "resets" (ratchets to a new, less favorable value) on the very same bar a flip would
     * otherwise trigger - an easy case to miss since every band-stickiness test elsewhere in this
     * suite uses data where that coincidence never happens.
     * <p>
     * Uses atrLength=1 (so ATR is just the true range itself, no Wilder-smoothing carry-over to
     * track) and multiplier=1 for hand-verifiable arithmetic. Candle 0 establishes an UP trend
     * with lowerBand ("up1") = 106. Candle 1's own basic lower band recomputes to 108 (above its
     * own close of 107) - comparing close against *this* value would wrongly signal a breakdown
     * (107 &lt; 108) and flip to DOWN, but close (107) is still above candle 0's carried band
     * (106), so the correct - and Pine-matching - result is to stay UP.
     */
    @Test
    void trendFlipComparesAgainstThePreviousBarsBandNotTheCurrentlyRecomputedOne() {
        List<Candle> candles = List.of(
                candle(0, 112, 108, 110),
                candle(1, 111, 109, 107)
        );

        List<Optional<SuperTrendResult>> results = CALCULATOR.calculate(candles, 1, BigDecimal.ONE);

        assertThat(results.get(0)).isPresent();
        assertThat(results.get(0).get().direction()).isEqualTo(SuperTrendDirection.UP);
        assertThat(results.get(0).get().lowerBand()).isEqualByComparingTo("106.0");

        assertThat(results.get(1)).isPresent();
        assertThat(results.get(1).get().lowerBand()).isEqualByComparingTo("108.0"); // this bar's own recomputed band - would wrongly suggest a breakdown if compared directly
        assertThat(results.get(1).get().direction()).isEqualTo(SuperTrendDirection.UP); // stays UP: close (107) is still above the *previous* bar's band (106)
        assertThat(results.get(1).get().superTrend()).isEqualByComparingTo("108.0");
    }

    @Test
    void incrementalRecomputeReturnsEmptyWhenAlreadyUpToDate() {
        List<Candle> candles = fiveCandleScenario();
        OffsetDateTime lastCandleCloseTime = candles.get(candles.size() - 1).closeTime();

        List<SuperTrendResult> results = CALCULATOR.calculateIncremental(candles, 2, BigDecimal.ONE, lastCandleCloseTime, false);

        assertThat(results).isEmpty();
    }
}
