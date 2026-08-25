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
     * Null if any argument is null. Appends {@code USDT} to {@code symbol} unless it already ends
     * with it. A {@link AssetVenue#FUTURES} asset gets a trailing {@code .P} on top of that —
     * TradingView's suffix for a Binance perpetual futures contract (e.g. {@code BITOUSDT.P}) —
     * since without it TradingView resolves the symbol against the spot market instead.
     * <p>
     * Also null for {@link AssetVenue#EXCHANGE}: this logic is Binance-specific (USDT pairing,
     * {@code provider.name()} as the TradingView exchange prefix), and a real equity's actual
     * TradingView exchange (NASDAQ/NYSE/OTC/SHG) isn't data this system stores per asset — a
     * fabricated link (e.g. {@code TIINGO:AAPLUSDT}) would be worse than none.
     */
    public static String generateUrl(Provider provider, String symbol, Timeframe timeframe, AssetVenue venue) {
        if (provider == null || symbol == null || timeframe == null || venue == null || venue == AssetVenue.EXCHANGE) {
            return null;
        }
        String fullSymbol = symbol.endsWith("USDT") ? symbol : symbol + "USDT";
        if (venue == AssetVenue.FUTURES) {
            fullSymbol = fullSymbol + ".P";
        }
        String encodedSymbol = encode(provider.name() + ":" + fullSymbol);
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
