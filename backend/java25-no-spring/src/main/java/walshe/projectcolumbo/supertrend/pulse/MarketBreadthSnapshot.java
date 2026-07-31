package walshe.projectcolumbo.supertrend.pulse;

import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MarketBreadthSnapshot(
        Timeframe timeframe,
        OffsetDateTime snapshotCloseTime,
        int bullishCount,
        int bearishCount,
        int missingCount,
        int totalAssets,
        BigDecimal bullishRatio
) {
}
