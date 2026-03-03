package walshe.projectcolumbo.api.v1.dto;

import walshe.projectcolumbo.persistence.TrendState;
import java.time.OffsetDateTime;

public record SignalStateDto(
    String symbol,
    TrendState trendState,
    OffsetDateTime lastFlipTime,
    Long daysSinceFlip,
    String tradingviewUrl
) {}
