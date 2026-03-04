package walshe.projectcolumbo.persistence.model;

import java.time.OffsetDateTime;

public record SignalStateResult(
        OffsetDateTime closeTime,
        TrendState trendState,
        SignalEvent event
) {
}
