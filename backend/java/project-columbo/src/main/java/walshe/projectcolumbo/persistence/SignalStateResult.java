package walshe.projectcolumbo.persistence;

import java.time.OffsetDateTime;

public record SignalStateResult(
        OffsetDateTime closeTime,
        TrendState trendState,
        SignalEvent event
) {
}
