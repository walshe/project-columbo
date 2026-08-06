package walshe.projectcolumbo.supertrend.persistence;

import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.AssetVenue;
import walshe.projectcolumbo.supertrend.shared.Provider;

import java.util.Objects;

public record Asset(long id, String symbol, Provider provider, boolean active, AssetClass assetClass, AssetVenue venue) {
    public Asset {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(assetClass, "assetClass must not be null");
        Objects.requireNonNull(venue, "venue must not be null");
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
    }
}
