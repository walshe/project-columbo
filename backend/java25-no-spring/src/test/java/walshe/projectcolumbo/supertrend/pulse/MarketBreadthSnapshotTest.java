package walshe.projectcolumbo.supertrend.pulse;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketBreadthSnapshotTest {

    private static final OffsetDateTime CLOSE_TIME = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void rejectsBullishRatioAboveOne() {
        assertThatThrownBy(() -> snapshot(1, 0, 0, 1, new BigDecimal("1.0001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bullishRatio");
    }

    @Test
    void rejectsBullishRatioBelowZero() {
        assertThatThrownBy(() -> snapshot(0, 1, 0, 1, new BigDecimal("-0.0001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bullishRatio");
    }

    @Test
    void rejectsNegativeCount() {
        assertThatThrownBy(() -> snapshot(-1, 0, 0, 1, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void acceptsBoundaryRatiosZeroAndOne() {
        snapshot(0, 0, 0, 0, BigDecimal.ZERO);
        snapshot(1, 0, 0, 1, BigDecimal.ONE);
    }

    @Test
    void acceptsNullAssetClassAsCombined() {
        MarketBreadthSnapshot combined = new MarketBreadthSnapshot(Timeframe.D1, CLOSE_TIME, null, 1, 0, 0, 1, BigDecimal.ONE);

        assertThat(combined.assetClass()).isNull();
    }

    @Test
    void acceptsASpecificAssetClass() {
        MarketBreadthSnapshot stockOnly =
                new MarketBreadthSnapshot(Timeframe.D1, CLOSE_TIME, AssetClass.STOCK, 1, 0, 0, 1, BigDecimal.ONE);

        assertThat(stockOnly.assetClass()).isEqualTo(AssetClass.STOCK);
    }

    private static MarketBreadthSnapshot snapshot(int bullish, int bearish, int missing, int total, BigDecimal ratio) {
        return new MarketBreadthSnapshot(Timeframe.D1, CLOSE_TIME, null, bullish, bearish, missing, total, ratio);
    }
}
