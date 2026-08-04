package walshe.projectcolumbo.supertrend.ingestion;

import java.util.Objects;

/** Thrown when a provider does not recognize a symbol — the asset should be deactivated. */
public class InvalidSymbolException extends RuntimeException {

    public InvalidSymbolException(String symbol) {
        super("Provider does not recognize symbol: " + messageSymbol(symbol));
    }

    private static String messageSymbol(String symbol) {
        return Objects.requireNonNull(symbol, "symbol must not be null");
    }
}
