package walshe.projectcolumbo.supertrend.indicator;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandleTest {

    private static final OffsetDateTime OPEN = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime CLOSE = OPEN.plusDays(1);
    private static final BigDecimal PRICE = BigDecimal.TEN;

    @Test
    void rejectsNullOpenTime() {
        assertThatThrownBy(() -> candle(null, PRICE, PRICE, PRICE, PRICE, BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsZeroOpenPrice() {
        assertThatThrownBy(() -> candle(OPEN, BigDecimal.ZERO, PRICE, PRICE, PRICE, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void rejectsNegativeHighPrice() {
        assertThatThrownBy(() -> candle(OPEN, PRICE, new BigDecimal("-1"), PRICE, PRICE, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void rejectsNegativeVolume() {
        assertThatThrownBy(() -> candle(OPEN, PRICE, PRICE, PRICE, PRICE, new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("volume");
    }

    @Test
    void acceptsZeroVolume() {
        candle(OPEN, PRICE, PRICE, PRICE, PRICE, BigDecimal.ZERO);
    }

    private static Candle candle(
            OffsetDateTime openTime, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {
        return new Candle(openTime, CLOSE, Timeframe.D1, open, high, low, close, volume);
    }
}
