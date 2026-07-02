package walshe.projectcolumbo.api.v1.util;

import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.Timeframe;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class TradingViewUtil {
    public static String generateUrl(MarketProvider provider, String symbol, Timeframe timeframe) {
        if (provider == null || symbol == null || timeframe == null) {
            return null;
        }
        String fullSymbol = symbol.endsWith("USDT") ? symbol : symbol + "USDT";
        String encodedSymbol = URLEncoder.encode(provider.name() + ":" + fullSymbol, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedInterval = URLEncoder.encode(timeframe.getValue(), StandardCharsets.UTF_8).replace("+", "%20");
        return String.format("https://www.tradingview.com/chart/?symbol=%s&interval=%s",
                encodedSymbol,
                encodedInterval);
    }

    /**
     * Extracts the TradingView watchlist token ({@code EXCHANGE:SYMBOL}, e.g. {@code BINANCE:BTCUSDT})
     * from a chart URL produced by {@link #generateUrl}, by decoding its {@code symbol=} query param.
     * Returns {@code null} for a null URL or one without a {@code symbol=} param.
     */
    public static String watchlistSymbol(String tradingviewUrl) {
        if (tradingviewUrl == null) {
            return null;
        }
        String marker = "symbol=";
        int start = tradingviewUrl.indexOf(marker);
        if (start < 0) {
            return null;
        }
        start += marker.length();
        int end = tradingviewUrl.indexOf('&', start);
        String raw = end < 0 ? tradingviewUrl.substring(start) : tradingviewUrl.substring(start, end);
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }
}
