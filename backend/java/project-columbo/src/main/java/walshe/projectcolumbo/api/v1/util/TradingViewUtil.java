package walshe.projectcolumbo.api.v1.util;

import walshe.projectcolumbo.persistence.MarketProvider;
import walshe.projectcolumbo.persistence.Timeframe;

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
}
