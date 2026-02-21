package walshe.projectcolumbo.persistence;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SignalStateCalculatorTest {

    private final SignalStateCalculator calculator = new SignalStateCalculator();

    @Test
    void shouldInitializeFirstRowWithNone() {
        SuperTrendIndicator indicator = createIndicator(SuperTrendDirection.UP);
        List<SignalStateResult> results = calculator.calculate(List.of(indicator));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).event()).isEqualTo(SignalEvent.NONE);
        assertThat(results.get(0).trendState()).isEqualTo(TrendState.BULLISH);
    }

    @Test
    void shouldProduceNoneOnSameDirection() {
        SuperTrendIndicator i1 = createIndicator(SuperTrendDirection.UP);
        SuperTrendIndicator i2 = createIndicator(SuperTrendDirection.UP);
        List<SignalStateResult> results = calculator.calculate(List.of(i1, i2));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).event()).isEqualTo(SignalEvent.NONE);
        assertThat(results.get(1).event()).isEqualTo(SignalEvent.NONE);
    }

    @Test
    void shouldProduceBullishReversalOnDownToUp() {
        SuperTrendIndicator i1 = createIndicator(SuperTrendDirection.DOWN);
        SuperTrendIndicator i2 = createIndicator(SuperTrendDirection.UP);
        List<SignalStateResult> results = calculator.calculate(List.of(i1, i2));

        assertThat(results).hasSize(2);
        assertThat(results.get(1).event()).isEqualTo(SignalEvent.BULLISH_REVERSAL);
        assertThat(results.get(1).trendState()).isEqualTo(TrendState.BULLISH);
    }

    @Test
    void shouldProduceBearishReversalOnUpToDown() {
        SuperTrendIndicator i1 = createIndicator(SuperTrendDirection.UP);
        SuperTrendIndicator i2 = createIndicator(SuperTrendDirection.DOWN);
        List<SignalStateResult> results = calculator.calculate(List.of(i1, i2));

        assertThat(results).hasSize(2);
        assertThat(results.get(1).event()).isEqualTo(SignalEvent.BEARISH_REVERSAL);
        assertThat(results.get(1).trendState()).isEqualTo(TrendState.BEARISH);
    }

    @Test
    void shouldHandleIncrementalStart() {
        SuperTrendIndicator i1 = createIndicator(SuperTrendDirection.UP);
        List<SignalStateResult> results = calculator.calculate(List.of(i1), SuperTrendDirection.DOWN);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).event()).isEqualTo(SignalEvent.BULLISH_REVERSAL);
    }

    @Test
    void shouldHandleIncrementalNoChange() {
        SuperTrendIndicator i1 = createIndicator(SuperTrendDirection.UP);
        List<SignalStateResult> results = calculator.calculate(List.of(i1), SuperTrendDirection.UP);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).event()).isEqualTo(SignalEvent.NONE);
    }

    private SuperTrendIndicator createIndicator(SuperTrendDirection direction) {
        SuperTrendIndicator indicator = new SuperTrendIndicator();
        indicator.setDirection(direction);
        indicator.setCloseTime(OffsetDateTime.now());
        return indicator;
    }
}
