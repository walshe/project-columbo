package walshe.projectcolumbo.supertrend.signal;

import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.time.OffsetDateTime;
import java.util.Objects;

public record SignalState(
        long assetId,
        Timeframe timeframe,
        OffsetDateTime closeTime,
        TrendState trendState,
        SignalEvent event
) {
    public SignalState {
        Objects.requireNonNull(timeframe, "timeframe must not be null");
        Objects.requireNonNull(closeTime, "closeTime must not be null");
        Objects.requireNonNull(trendState, "trendState must not be null");
        Objects.requireNonNull(event, "event must not be null");
    }
}
