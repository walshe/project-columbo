package walshe.projectcolumbo.supertrend.signal;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.indicator.SuperTrendCalculator;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ProvisionalTrendServiceTest {

    private static final OffsetDateTime BASE_TIME = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final SuperTrendCalculator CALCULATOR = new SuperTrendCalculator();

    @Test
    void tooLittleCommittedHistoryReturnsEmpty() {
        List<Candle> committedW1 = risingWeeks(3);
        List<Candle> weekToDateD1 = List.of(dayCandle(0, 215, 216, 50, 60));

        Optional<ProvisionalTrendResult> result = ProvisionalTrendService.compute(CALCULATOR, committedW1, weekToDateD1);

        assertThat(result).isEmpty();
    }

    @Test
    void sharpCounterMoveFlipsTheProvisionalDirection() {
        List<Candle> committedW1 = risingWeeks(12);
        // Last committed close is ~218; this week-to-date candle breaks dramatically below any
        // plausible trailing band built from a smooth uptrend, so it should flip the direction.
        List<Candle> weekToDateD1 = List.of(dayCandle(0, 215, 216, 50, 60));

        Optional<ProvisionalTrendResult> result = ProvisionalTrendService.compute(CALCULATOR, committedW1, weekToDateD1);

        assertThat(result).isPresent();
        assertThat(result.get().direction()).isEqualTo(TrendState.BEARISH);
        assertThat(result.get().flipLevel()).isNotNull();
    }

    @Test
    void continuedTrendKeepsTheProvisionalDirection() {
        List<Candle> committedW1 = risingWeeks(12);
        // Continues the same established pattern by a modest, in-trend amount.
        List<Candle> weekToDateD1 = List.of(dayCandle(0, 218, 230, 210, 225));

        Optional<ProvisionalTrendResult> result = ProvisionalTrendService.compute(CALCULATOR, committedW1, weekToDateD1);

        assertThat(result).isPresent();
        assertThat(result.get().direction()).isEqualTo(TrendState.BULLISH);
    }

    /** A clean, steadily-rising 10-point-per-week sequence - simple enough that a dramatic counter-move is unambiguous either way. */
    private static List<Candle> risingWeeks(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> weekCandle(i, 100 + i * 10, 115 + i * 10, 95 + i * 10, 108 + i * 10))
                .toList();
    }

    private static Candle weekCandle(int weekOffset, long open, long high, long low, long close) {
        OffsetDateTime closeTime = BASE_TIME.plusWeeks(weekOffset + 1);
        return new Candle(
                closeTime.minusWeeks(1), closeTime, Timeframe.W1,
                BigDecimal.valueOf(open), BigDecimal.valueOf(high), BigDecimal.valueOf(low), BigDecimal.valueOf(close), BigDecimal.ZERO);
    }

    private static Candle dayCandle(int dayOffset, long open, long high, long low, long close) {
        OffsetDateTime closeTime = BASE_TIME.plusWeeks(12).plusDays(dayOffset + 1);
        return new Candle(
                closeTime.minusDays(1), closeTime, Timeframe.D1,
                BigDecimal.valueOf(open), BigDecimal.valueOf(high), BigDecimal.valueOf(low), BigDecimal.valueOf(close), BigDecimal.ZERO);
    }
}
