package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

/** Both fields are optional - a missing or empty body is equivalent to {@code {}}. */
public record IngestionTriggerRequest(Provider provider, Timeframe timeframe) {
    public IngestionTriggerRequest {
        if (provider == null) {
            provider = Provider.BINANCE;
        }
        if (timeframe == null) {
            timeframe = Timeframe.D1;
        }
    }
}
