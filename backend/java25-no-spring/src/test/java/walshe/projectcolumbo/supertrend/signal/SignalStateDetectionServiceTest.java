package walshe.projectcolumbo.supertrend.signal;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.indicator.SuperTrendDirection;
import walshe.projectcolumbo.supertrend.indicator.SuperTrendResult;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SignalStateDetectionServiceTest {

    private static final OffsetDateTime BASE_TIME = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final long ASSET_ID = 42L;

    @Test
    void warmUpCandlesGetUnknownStateAndNoEvent() {
        List<Candle> candles = candles(3);
        List<Optional<SuperTrendResult>> results = List.of(Optional.empty(), Optional.empty(), Optional.empty());

        List<SignalState> states = SignalStateDetectionService.detect(ASSET_ID, Timeframe.D1, candles, results);

        assertThat(states).extracting(SignalState::trendState).containsExactly(TrendState.UNKNOWN, TrendState.UNKNOWN, TrendState.UNKNOWN);
        assertThat(states).extracting(SignalState::event).containsOnly(SignalEvent.NONE);
    }

    @Test
    void firstConcreteStateAfterWarmUpHasNoReversalEvent() {
        List<Candle> candles = candles(2);
        List<Optional<SuperTrendResult>> results = List.of(Optional.empty(), present(SuperTrendDirection.UP, candles.get(1)));

        List<SignalState> states = SignalStateDetectionService.detect(ASSET_ID, Timeframe.D1, candles, results);

        assertThat(states.get(1).trendState()).isEqualTo(TrendState.BULLISH);
        assertThat(states.get(1).event()).isEqualTo(SignalEvent.NONE);
    }

    @Test
    void directionChangeEmitsCorrectReversalEventOnlyOnTheFlip() {
        List<Candle> candles = candles(5);
        List<Optional<SuperTrendResult>> results = List.of(
                present(SuperTrendDirection.UP, candles.get(0)),
                present(SuperTrendDirection.UP, candles.get(1)),
                present(SuperTrendDirection.DOWN, candles.get(2)),
                present(SuperTrendDirection.DOWN, candles.get(3)),
                present(SuperTrendDirection.UP, candles.get(4))
        );

        List<SignalState> states = SignalStateDetectionService.detect(ASSET_ID, Timeframe.D1, candles, results);

        assertThat(states).extracting(SignalState::trendState)
                .containsExactly(TrendState.BULLISH, TrendState.BULLISH, TrendState.BEARISH, TrendState.BEARISH, TrendState.BULLISH);
        assertThat(states).extracting(SignalState::event)
                .containsExactly(SignalEvent.NONE, SignalEvent.NONE, SignalEvent.BEARISH_REVERSAL, SignalEvent.NONE, SignalEvent.BULLISH_REVERSAL);
    }

    @Test
    void closeTimeAndAssetIdAndTimeframeArePropagatedFromTheCandle() {
        List<Candle> candles = candles(1);

        List<SignalState> states = SignalStateDetectionService.detect(ASSET_ID, Timeframe.W1, candles, List.of(Optional.empty()));

        SignalState state = states.get(0);
        assertThat(state.assetId()).isEqualTo(ASSET_ID);
        assertThat(state.timeframe()).isEqualTo(Timeframe.W1);
        assertThat(state.closeTime()).isEqualTo(candles.get(0).closeTime());
    }

    private static List<Candle> candles(int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> {
            OffsetDateTime closeTime = BASE_TIME.plusDays(i + 1);
            return new Candle(
                    closeTime.minusDays(1),
                    closeTime,
                    Timeframe.D1,
                    BigDecimal.valueOf(100 + i),
                    BigDecimal.valueOf(110 + i),
                    BigDecimal.valueOf(90 + i),
                    BigDecimal.valueOf(105 + i),
                    BigDecimal.valueOf(1000)
            );
        }).toList();
    }

    private static Optional<SuperTrendResult> present(SuperTrendDirection direction, Candle candle) {
        return Optional.of(new SuperTrendResult(candle.closeTime(), BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, direction));
    }
}
