package walshe.projectcolumbo.supertrend.shared;

/**
 * Which trading venue's market-data client an asset routes to. Binance's spot and futures are
 * separate products with separate hosts and separate klines paths, not just a configurable base
 * URL. {@code EXCHANGE} represents a real securities exchange (e.g. Tiingo) where that spot/
 * futures split doesn't apply. {@code MEXC} represents MEXC's own single spot market, which -
 * unlike Binance - covers crypto and tokenized real-equity pairs through the same client and
 * endpoint, so it isn't split the way Binance's spot/futures or reused from {@code EXCHANGE} the
 * way a second real-exchange provider might be. Independent of {@link AssetClass}: most crypto is
 * spot, but some crypto is futures-only, and a stock/ETF asset can be tokenized on Binance,
 * tokenized on MEXC, or a real equity via Tiingo.
 */
public enum AssetVenue {
    SPOT,
    FUTURES,
    EXCHANGE,
    MEXC
}
