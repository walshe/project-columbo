package walshe.projectcolumbo.api.v1.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MarketPulseDto(
    OffsetDateTime snapshotCloseTime,
    int bullishCount,
    int bearishCount,
    int missingCount,
    int totalAssets,
    BigDecimal bullishRatio
) {}
