package walshe.projectcolumbo.supertrend.persistence;

import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.AssetVenue;
import walshe.projectcolumbo.supertrend.shared.Provider;

import java.util.Objects;

/**
 * @param name           optional human-readable display name (e.g. "Apple Inc"), distinct from the tradeable {@code symbol} - null when not known/set.
 * @param tradingviewRef verified TradingView {@code EXCHANGE:SYMBOL} reference (e.g. "NASDAQ:AAPL", "OTC:SSNLF") for an {@code EXCHANGE}-venue asset - null when not yet verified; never derived from {@code symbol} at read time, since Tiingo's own ticker doesn't always match TradingView's (e.g. "BRK-A" vs. "BRK.A").
 */
public record Asset(long id, String symbol, Provider provider, boolean active, AssetClass assetClass, AssetVenue venue, String name, String tradingviewRef) {
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
