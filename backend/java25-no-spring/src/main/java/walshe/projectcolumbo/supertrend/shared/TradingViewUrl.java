package walshe.projectcolumbo.supertrend.shared;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Builds TradingView chart deep links for an asset, and extracts a watchlist token back out of one. */
public final class TradingViewUrl {

    private TradingViewUrl() {
    }

    /**
     * Null if {@code provider}, {@code symbol}, {@code timeframe}, or {@code venue} is null.
     * Appends {@code USDT} to {@code symbol} unless it already ends with it. A {@link
     * AssetVenue#FUTURES} asset gets a trailing {@code .P} on top of that — TradingView's suffix
     * for a Binance perpetual futures contract (e.g. {@code BITOUSDT.P}) — since without it
     * TradingView resolves the symbol against the spot market instead.
     * <p>
     * For {@link AssetVenue#EXCHANGE}, this Binance-specific construction (USDT pairing, {@code
     * provider.name()} as the exchange prefix) doesn't apply — {@code tradingviewRef} (a verified
     * {@code EXCHANGE:SYMBOL} string, e.g. {@code "NASDAQ:AAPL"}) is used directly instead. Null
     * when no verified ref is available for that asset: a fabricated link (wrong exchange, or
     * Tiingo's own ticker format not matching TradingView's — e.g. {@code "BRK-A"} vs. {@code
     * "BRK.A"}) would be worse than none.
     *
     * @param tradingviewRef a verified TradingView {@code EXCHANGE:SYMBOL} reference for an
     *                       {@code EXCHANGE}-venue asset (see {@code Asset.tradingviewRef()});
     *                       ignored for every other venue.
     */
    public static String generateUrl(Provider provider, String symbol, Timeframe timeframe, AssetVenue venue, String tradingviewRef) {
        if (provider == null || symbol == null || timeframe == null || venue == null) {
            return null;
        }
        if (venue == AssetVenue.EXCHANGE) {
            return (tradingviewRef == null || tradingviewRef.isBlank()) ? null : chartUrl(tradingviewRef, timeframe);
        }
        String fullSymbol = symbol.endsWith("USDT") ? symbol : symbol + "USDT";
        if (venue == AssetVenue.FUTURES) {
            fullSymbol = fullSymbol + ".P";
        }
        return chartUrl(provider.name() + ":" + fullSymbol, timeframe);
    }

    private static String chartUrl(String tradingviewSymbol, Timeframe timeframe) {
        String encodedSymbol = encode(tradingviewSymbol);
        String encodedInterval = encode(interval(timeframe));
        return "https://www.tradingview.com/chart/?symbol=" + encodedSymbol + "&interval=" + encodedInterval;
    }

    /**
     * Extracts the TradingView watchlist token ({@code EXCHANGE:SYMBOL}, e.g. {@code BINANCE:BTCUSDT})
     * from a chart URL produced by {@link #generateUrl}, by decoding its {@code symbol=} query param.
     * Null for a null/malformed URL, or one without a {@code symbol=} param. Parses via
     * {@link URI#getRawQuery()} (not a substring/regex over the whole URL) so a trailing
     * {@code #fragment} - even one containing {@code symbol=} - is correctly excluded, and splits on
     * the raw (un-decoded) {@code &} so an encoded {@code %26} inside the symbol value is never
     * mistaken for a parameter separator.
     */
    public static String watchlistSymbol(String tradingviewUrl) {
        if (tradingviewUrl == null) {
            return null;
        }
        String rawQuery;
        try {
            rawQuery = new URI(tradingviewUrl).getRawQuery();
        } catch (URISyntaxException e) {
            return null;
        }
        if (rawQuery == null) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.startsWith("symbol=")) {
                return URLDecoder.decode(pair.substring("symbol=".length()), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String interval(Timeframe timeframe) {
        return switch (timeframe) {
            case D1 -> "1D";
            case W1 -> "1W";
        };
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
