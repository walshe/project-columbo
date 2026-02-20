package walshe.projectcolumbo.marketdata;

import java.util.List;

/**
 * Interface for fetching market data from external providers.
 */
public interface MarketDataProvider {

    /**
     * Fetch daily OHLC candles for a given asset from the provider.
     *
     * @param providerId the asset's ID as defined by the provider (e.g., 'bitcoin' for CoinGecko)
     * @return a list of {@link CandleDto}
     */
    List<CandleDto> fetchDailyCandles(String providerId);

    /**
     * Get the name of the provider.
     * @return the provider name (e.g., 'BINANCE')
     */
    default String getProviderName() {
        return this.getClass().getSimpleName().toUpperCase().replace("MARKETDATAPROVIDER", "");
    }
}
