package walshe.projectcolumbo.persistence.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Market data source the asset's candles come from. Currently only BINANCE.")
public enum MarketProvider {
    BINANCE
}
