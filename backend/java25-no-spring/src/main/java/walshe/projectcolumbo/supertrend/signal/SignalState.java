package walshe.projectcolumbo.supertrend.signal;

import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.time.OffsetDateTime;

public record SignalState(
        long assetId,
        Timeframe timeframe,
        OffsetDateTime closeTime,
        TrendState trendState,
        SignalEvent event
) {
}
