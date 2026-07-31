package walshe.projectcolumbo.supertrend.ingestion;

/** Thrown when a provider does not recognize a symbol — the asset should be deactivated. */
public class InvalidSymbolException extends RuntimeException {

    public InvalidSymbolException(String symbol) {
        super("Provider does not recognize symbol: " + symbol);
    }
}
