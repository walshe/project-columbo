package walshe.projectcolumbo.persistence.service;

import walshe.projectcolumbo.persistence.entity.Candle;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmaCalculatorTest {

    private final EmaCalculator calculator = new EmaCalculator();

    @Test
    void shouldReturnEmptyWhenInsufficientCandles() {
        List<Candle> candles = new ArrayList<>();
        candles.add(createCandle(BigDecimal.TEN, 0));
        candles.add(createCandle(BigDecimal.valueOf(20), 1));

        List<EmaCalculator.EmaResult> results = calculator.calculate(candles, 3);

        assertThat(results).isEmpty();
    }

    @Test
    void shouldComputeSeedValueAsSma() {
        // period=3, k = 2/(3+1) = 0.5
        // Seed = SMA of [10, 20, 30] = 60/3 = 20.0
        List<Candle> candles = new ArrayList<>();
        candles.add(createCandle(BigDecimal.valueOf(10), 0));
        candles.add(createCandle(BigDecimal.valueOf(20), 1));
        candles.add(createCandle(BigDecimal.valueOf(30), 2));

        List<EmaCalculator.EmaResult> results = calculator.calculate(candles, 3);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).emaValue()).isEqualByComparingTo("20.0000000000");
        assertThat(results.get(0).closeTime())
                .isEqualTo(OffsetDateTime.parse("2024-01-01T00:00:00Z").plusDays(2));
    }

    @Test
    void shouldApplyEmaFormulaAfterSeed() {
        // period=3, k=0.5
        // Seed (candles 0-2): EMA = SMA([10,20,30]) = 20.0
        // Candle 3: close=40, EMA = 40*0.5 + 20*0.5 = 30.0
        List<Candle> candles = new ArrayList<>();
        candles.add(createCandle(BigDecimal.valueOf(10), 0));
        candles.add(createCandle(BigDecimal.valueOf(20), 1));
        candles.add(createCandle(BigDecimal.valueOf(30), 2));
        candles.add(createCandle(BigDecimal.valueOf(40), 3));

        List<EmaCalculator.EmaResult> results = calculator.calculate(candles, 3);

        assertThat(results).hasSize(2);
        assertThat(results.get(1).emaValue()).isEqualByComparingTo("30.0");
    }

    @Test
    void calculateFromValues_shouldMatchCalculate() {
        List<Candle> candles = new ArrayList<>();
        candles.add(createCandle(BigDecimal.valueOf(10), 0));
        candles.add(createCandle(BigDecimal.valueOf(20), 1));
        candles.add(createCandle(BigDecimal.valueOf(30), 2));
        candles.add(createCandle(BigDecimal.valueOf(40), 3));
        candles.add(createCandle(BigDecimal.valueOf(50), 4));

        List<EmaCalculator.EmaResult> fromCandles = calculator.calculate(candles, 3);

        List<BigDecimal> closes = List.of(
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(30),
                BigDecimal.valueOf(40),
                BigDecimal.valueOf(50));
        List<OffsetDateTime> times = candles.stream().map(Candle::getCloseTime).toList();
        List<EmaCalculator.EmaResult> fromValues = calculator.calculateFromValues(closes, times, 3);

        assertThat(fromCandles).hasSameSizeAs(fromValues);
        for (int i = 0; i < fromCandles.size(); i++) {
            assertThat(fromCandles.get(i).emaValue())
                    .isEqualByComparingTo(fromValues.get(i).emaValue());
        }
    }

    private Candle createCandle(BigDecimal close, int day) {
        Candle c = new Candle();
        c.setClose(close);
        c.setCloseTime(OffsetDateTime.parse("2024-01-01T00:00:00Z").plusDays(day));
        return c;
    }
}
