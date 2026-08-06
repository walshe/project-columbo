package walshe.projectcolumbo.supertrend.shared;

/**
 * Which Binance product an asset trades on — spot and futures are separate products with
 * separate hosts and separate klines paths, not just a configurable base URL. Independent of
 * {@link AssetClass}: most crypto is spot, but some crypto is futures-only, and every
 * stock/ETF/commodity asset is futures-only.
 */
public enum AssetVenue {
    SPOT,
    FUTURES
}
