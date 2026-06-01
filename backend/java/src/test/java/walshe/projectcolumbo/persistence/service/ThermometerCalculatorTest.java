package walshe.projectcolumbo.persistence.service;

import walshe.projectcolumbo.persistence.entity.Candle;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ThermometerCalculatorTest {

    private final ThermometerCalculator calculator =
            new ThermometerCalculator(new EmaCalculator());

    // ---- guard: insufficient candles ----

    @Test
    void shouldReturnEmpty_whenNullInput() {
        assertThat(calculator.calculate(null)).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenFewerThan2Candles() {
        assertThat(calculator.calculate(List.of())).isEmpty();
        assertThat(calculator.calculate(List.of(createCandle(50000, 51000, 49000, 1)))).isEmpty();
    }

    // ---- temperature formula ----

    @Test
    void shouldReturnZero_forInsideBar() {
        // Today's bar is completely inside yesterday's range
        // yesterday: high=52000, low=48000   today: high=51000, low=49000
        // highDiff = 51000 - 52000 = -1000 (inside)
        // lowDiff  = 48000 - 49000 = -1000 (inside)
        // temp = MAX(-1000, -1000, 0) = 0
        Candle yesterday = createCandle(50000, 52000, 48000, 1);
        Candle today     = createCandle(50000, 51000, 49000, 2);

        List<ThermometerCalculator.ThermometerResult> results =
                calculator.calculate(List.of(yesterday, today));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).temperature()).isEqualByComparingTo("0");
    }

    @Test
    void shouldUseHighDiff_whenTodayHighExceedsYesterday() {
        // yesterday: high=50000, low=48000   today: high=52000, low=48500
        // highDiff = 52000 - 50000 = 2000
        // lowDiff  = 48000 - 48500 = -500
        // temp = MAX(2000, -500, 0) = 2000
        Candle yesterday = createCandle(50000, 50000, 48000, 1);
        Candle today     = createCandle(50000, 52000, 48500, 2);

        List<ThermometerCalculator.ThermometerResult> results =
                calculator.calculate(List.of(yesterday, today));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).temperature()).isEqualByComparingTo("2000");
    }

    @Test
    void shouldUseLowDiff_whenTodayLowBreaksYesterday() {
        // yesterday: high=50000, low=48000   today: high=49500, low=46000
        // highDiff = 49500 - 50000 = -500 (inside)
        // lowDiff  = 48000 - 46000 = 2000
        // temp = MAX(-500, 2000, 0) = 2000
        Candle yesterday = createCandle(50000, 50000, 48000, 1);
        Candle today     = createCandle(50000, 49500, 46000, 2);

        List<ThermometerCalculator.ThermometerResult> results =
                calculator.calculate(List.of(yesterday, today));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).temperature()).isEqualByComparingTo("2000");
    }

    // ---- EMA null/non-null boundary ----

    @Test
    void shouldHaveNullEma_forFirst21Results_whenLessThan22TempValues() {
        // 22 candles → 21 temperature values → EMA period=22 requires 22 values → all null
        List<Candle> candles = buildCandlesWithAscendingHighs(22);

        List<ThermometerCalculator.ThermometerResult> results = calculator.calculate(candles);

        assertThat(results).hasSize(21);
        assertThat(results).allMatch(r -> r.temperatureEma() == null);
    }

    @Test
    void shouldHaveNonNullEma_startingAtResult22() {
        // 23 candles → 22 temperature values → EMA period=22 → 1 EMA value at index 21
        List<Candle> candles = buildCandlesWithAscendingHighs(23);

        List<ThermometerCalculator.ThermometerResult> results = calculator.calculate(candles);

        assertThat(results).hasSize(22);
        // First 21 have null ema
        for (int i = 0; i < 21; i++) {
            assertThat(results.get(i).temperatureEma())
                    .as("Expected null ema at index %d", i)
                    .isNull();
        }
        // Result at index 21 (the 22nd temperature value) has non-null ema
        assertThat(results.get(21).temperatureEma())
                .as("Expected non-null ema at index 21")
                .isNotNull()
                .isPositive();
    }

    @Test
    void shouldHaveNonNullEma_forLast3Results_when25Candles() {
        // 25 candles → 24 temperature values → 3 EMA values (indices 21, 22, 23)
        List<Candle> candles = buildCandlesWithAscendingHighs(25);

        List<ThermometerCalculator.ThermometerResult> results = calculator.calculate(candles);

        assertThat(results).hasSize(24);
        assertThat(results.get(21).temperatureEma()).isNotNull();
        assertThat(results.get(22).temperatureEma()).isNotNull();
        assertThat(results.get(23).temperatureEma()).isNotNull();
    }

    @Test
    void closeTime_shouldMatchTodayCandle() {
        // Result closeTime is today's candle closeTime, not yesterday's
        Candle yesterday = createCandle(50000, 50000, 48000, 1);
        Candle today     = createCandle(50000, 52000, 47000, 2);

        List<ThermometerCalculator.ThermometerResult> results =
                calculator.calculate(List.of(yesterday, today));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).closeTime()).isEqualTo(today.getCloseTime());
    }

    // ---- helpers ----

    /**
     * Build n candles with steadily ascending highs so temperatures are positive.
     * high[i] = 50000 + i * 100, low[i] = 49000 + i * 50.
     * Candles are sorted ascending by closeTime starting at 2024-01-01.
     */
    private List<Candle> buildCandlesWithAscendingHighs(int n) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double high = 50000 + i * 100;
            double low  = 49000 + i * 50;
            candles.add(createCandle(50000, high, low, i + 1));
        }
        return candles;
    }

    private Candle createCandle(double close, double high, double low, int dayOffset) {
        Candle c = new Candle();
        c.setClose(BigDecimal.valueOf(close));
        c.setHigh(BigDecimal.valueOf(high));
        c.setLow(BigDecimal.valueOf(low));
        c.setOpen(BigDecimal.valueOf(close));
        c.setCloseTime(OffsetDateTime.parse("2024-01-01T00:00:00Z").plusDays(dayOffset));
        return c;
    }
}
