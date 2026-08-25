package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.shared.Timeframe;

/** {@code timeframe} is optional - a missing or empty body is equivalent to {@code {}}. A run always covers every provider's assets; there is no per-provider scoping. */
public record IngestionTriggerRequest(Timeframe timeframe) {
    public IngestionTriggerRequest {
        if (timeframe == null) {
            timeframe = Timeframe.D1;
        }
    }
}
