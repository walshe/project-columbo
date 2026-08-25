package walshe.projectcolumbo.supertrend.shared;

/**
 * Which trading venue's market-data client an asset routes to. Binance's spot and futures are
 * separate products with separate hosts and separate klines paths, not just a configurable base
 * URL. {@code EXCHANGE} represents a real securities exchange (e.g. Tiingo) where that spot/
 * futures split doesn't apply. Independent of {@link AssetClass}: most crypto is spot, but some
 * crypto is futures-only, and every stock/ETF/commodity asset is either futures (tokenized on
 * Binance) or exchange (real equity via Tiingo).
 */
public enum AssetVenue {
    SPOT,
    FUTURES,
    EXCHANGE
}
