package walshe.projectcolumbo.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.Timeframe;

@Schema(description = "Request to trigger a market data ingestion + indicator pipeline run. "
        + "Both fields are optional and default when omitted.")
public record IngestionRequest(
    @Schema(description = "Market data provider to ingest from. Defaults to BINANCE when omitted.", example = "BINANCE")
    MarketProvider provider,

    @Schema(description = "Timeframe recorded on the run. Defaults to D1 when omitted.", example = "D1")
    Timeframe timeframe
) {
    public IngestionRequest {
        if (provider == null) provider = MarketProvider.BINANCE;
        if (timeframe == null) timeframe = Timeframe.D1;
    }
}
