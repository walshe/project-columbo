package walshe.projectcolumbo.supertrend.ingestion;

/** Unchecked wrapper for market data provider infra failures (network, HTTP errors, malformed responses). */
public class MarketDataProviderException extends RuntimeException {

    public MarketDataProviderException(String message) {
        super(message);
    }

    public MarketDataProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
