package walshe.projectcolumbo.supertrend.persistence;

import walshe.projectcolumbo.supertrend.shared.Provider;

import java.util.Objects;

public record Asset(long id, String symbol, Provider provider, boolean active) {
    public Asset {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
    }
}
