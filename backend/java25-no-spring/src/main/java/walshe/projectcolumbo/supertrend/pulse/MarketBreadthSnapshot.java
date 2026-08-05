package walshe.projectcolumbo.supertrend.pulse;

import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/** @param assetClass the class this snapshot is scoped to; null means combined across every class */
public record MarketBreadthSnapshot(
        Timeframe timeframe,
        OffsetDateTime snapshotCloseTime,
        AssetClass assetClass,
        int bullishCount,
        int bearishCount,
        int missingCount,
        int totalAssets,
        BigDecimal bullishRatio
) {
    public MarketBreadthSnapshot {
        Objects.requireNonNull(timeframe, "timeframe must not be null");
        Objects.requireNonNull(snapshotCloseTime, "snapshotCloseTime must not be null");
        Objects.requireNonNull(bullishRatio, "bullishRatio must not be null");
        if (bullishCount < 0 || bearishCount < 0 || missingCount < 0 || totalAssets < 0) {
            throw new IllegalArgumentException("counts must not be negative: bullish=" + bullishCount
                    + " bearish=" + bearishCount + " missing=" + missingCount + " total=" + totalAssets);
        }
        if (bullishRatio.compareTo(BigDecimal.ZERO) < 0 || bullishRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("bullishRatio must be between 0 and 1, was: " + bullishRatio);
        }
    }
}
