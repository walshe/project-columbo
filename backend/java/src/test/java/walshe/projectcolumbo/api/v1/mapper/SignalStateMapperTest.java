package walshe.projectcolumbo.api.v1.mapper;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.SignalState;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.SignalEvent;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.TrendState;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SignalStateMapperTest {

    private final OffsetDateTime now = OffsetDateTime.of(2024, 1, 10, 12, 0, 0, 0, ZoneOffset.UTC);

    private SignalState state(Asset asset, OffsetDateTime closeTime, TrendState trendState) {
        return new SignalState(asset, Timeframe.D1, IndicatorType.SUPERTREND, closeTime, trendState, SignalEvent.NONE);
    }

    private Asset asset() {
        Asset btc = new Asset("BTC", "Bitcoin", MarketProvider.BINANCE, true);
        btc.setId(1L);
        return btc;
    }

    @Test
    void computesPositivePctChangeSinceFlip() {
        Asset btc = asset();
        SignalState latest = state(btc, now.minusDays(1), TrendState.SUPERTREND_BULLISH);
        SignalState flip = state(btc, now.minusDays(5), TrendState.SUPERTREND_BULLISH);

        SignalStateDto dto = SignalStateMapper.toDto(latest, flip, now, BigDecimal.ZERO,
                new BigDecimal("100.00"), new BigDecimal("115.00"));

        assertThat(dto.pctChangeSinceFlip()).isEqualByComparingTo("15.00");
    }

    @Test
    void computesNegativePctChangeSinceFlip() {
        Asset btc = asset();
        SignalState latest = state(btc, now.minusDays(1), TrendState.SUPERTREND_BEARISH);
        SignalState flip = state(btc, now.minusDays(5), TrendState.SUPERTREND_BEARISH);

        SignalStateDto dto = SignalStateMapper.toDto(latest, flip, now, BigDecimal.ZERO,
                new BigDecimal("200.00"), new BigDecimal("190.00"));

        assertThat(dto.pctChangeSinceFlip()).isEqualByComparingTo("-5.00");
    }

    @Test
    void isNullWhenNoFlipPrice() {
        Asset btc = asset();
        SignalState latest = state(btc, now.minusDays(1), TrendState.SUPERTREND_BULLISH);
        SignalState flip = state(btc, now.minusDays(5), TrendState.SUPERTREND_BULLISH);

        SignalStateDto dto = SignalStateMapper.toDto(latest, flip, now, BigDecimal.ZERO,
                null, new BigDecimal("115.00"));

        assertThat(dto.pctChangeSinceFlip()).isNull();
    }

    @Test
    void isNullWhenNoLatestPrice() {
        Asset btc = asset();
        SignalState latest = state(btc, now.minusDays(1), TrendState.SUPERTREND_BULLISH);
        SignalState flip = state(btc, now.minusDays(5), TrendState.SUPERTREND_BULLISH);

        SignalStateDto dto = SignalStateMapper.toDto(latest, flip, now, BigDecimal.ZERO,
                new BigDecimal("100.00"), null);

        assertThat(dto.pctChangeSinceFlip()).isNull();
    }

    @Test
    void isNullWhenNoFlipRecorded() {
        Asset btc = asset();
        SignalState latest = state(btc, now.minusDays(1), TrendState.SUPERTREND_BULLISH);

        SignalStateDto dto = SignalStateMapper.toDto(latest, null, now, BigDecimal.ZERO,
                null, new BigDecimal("115.00"));

        assertThat(dto.pctChangeSinceFlip()).isNull();
    }
}
