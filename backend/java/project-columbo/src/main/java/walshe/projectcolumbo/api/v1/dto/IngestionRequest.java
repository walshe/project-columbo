package walshe.projectcolumbo.api.v1.dto;

import walshe.projectcolumbo.persistence.MarketProvider;
import walshe.projectcolumbo.persistence.Timeframe;

public record IngestionRequest(
    MarketProvider provider,
    Timeframe timeframe
) {
    public IngestionRequest {
        if (provider == null) provider = MarketProvider.BINANCE;
        if (timeframe == null) timeframe = Timeframe.D1;
    }
}
