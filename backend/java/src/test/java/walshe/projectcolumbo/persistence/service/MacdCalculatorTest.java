package walshe.projectcolumbo.persistence.service;

import walshe.projectcolumbo.persistence.entity.Candle;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MacdCalculatorTest {

    private final EmaCalculator emaCalculator = new EmaCalculator();
    private final MacdCalculator calculator = new MacdCalculator(emaCalculator);

    @Test
    void shouldReturnEmptyWhenInsufficientCandles() {
        // 33 candles — one fewer than the 34 required
        List<Candle> candles = createCandlesSeries(33);

        assertThat(calculator.calculate(candles)).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenNullInput() {
        assertThat(calculator.calculate(null)).isEmpty();
    }

    @Test
    void shouldProduceResultWithExactlyMinimumCandles() {
        // 34 candles — the minimum for first histogram value
        List<Candle> candles = createCandlesSeries(34);

        assertThat(calculator.calculate(candles)).hasSize(1);
    }

    @Test
    void shouldProduceCorrectNumberOfResults() {
        // 40 candles: 40 - 34 + 1 = 7 results
        List<Candle> candles = createCandlesSeries(40);

        assertThat(calculator.calculate(candles)).hasSize(7);
    }

    @Test
    void shouldHaveHistogramEqualToMacdLineMinusSignalLine() {
        List<Candle> candles = createCandlesSeries(40);

        List<MacdCalculator.MacdResult> results = calculator.calculate(candles);

        for (MacdCalculator.MacdResult result : results) {
            assertThat(result.histogram())
                    .isEqualByComparingTo(result.macdLine().subtract(result.signalLine()));
        }
    }

    @Test
    void shouldHaveConsistentCloseTimesWithInputCandles() {
        List<Candle> candles = createCandlesSeries(40);

        List<MacdCalculator.MacdResult> results = calculator.calculate(candles);

        // First result close time should match candle at index 33 (34th candle)
        assertThat(results.get(0).closeTime())
                .isEqualTo(candles.get(33).getCloseTime());
    }

    private Candle createCandle(BigDecimal close, int day) {
        Candle c = new Candle();
        c.setClose(close);
        c.setCloseTime(OffsetDateTime.parse("2024-01-01T00:00:00Z").plusDays(day));
        return c;
    }

    private List<Candle> createCandlesSeries(int count) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            // Alternating series to avoid degenerate zero-movement case
            candles.add(createCandle(BigDecimal.valueOf(100 + (i % 5)), i));
        }
        return candles;
    }
}
