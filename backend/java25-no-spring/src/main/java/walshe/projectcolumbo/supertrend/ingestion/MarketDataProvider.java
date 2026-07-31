package walshe.projectcolumbo.supertrend.ingestion;

import walshe.projectcolumbo.supertrend.indicator.Candle;

import java.util.List;

/**
 * Fetches daily candles from an external market data source. Binance is the only
 * implementation actually wired up; a second provider is added here (not speculatively
 * beforehand) if one is ever genuinely needed.
 */
public interface MarketDataProvider {

    /**
     * Fetches daily candles for a symbol within {@code [startTimeMs, endTimeMs)}, ordered
     * oldest to newest.
     *
     * @throws InvalidSymbolException if the provider does not recognize the symbol
     */
    List<Candle> fetchDailyCandles(String symbol, long startTimeMs, long endTimeMs);
}
