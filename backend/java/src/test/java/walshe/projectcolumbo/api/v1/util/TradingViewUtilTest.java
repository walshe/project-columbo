package walshe.projectcolumbo.api.v1.util;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.Timeframe;

import static org.assertj.core.api.Assertions.assertThat;

class TradingViewUtilTest {

    @Test
    void watchlistSymbolRoundTripsFromGeneratedUrl() {
        String url = TradingViewUtil.generateUrl(MarketProvider.BINANCE, "BTCUSDT", Timeframe.D1);
        assertThat(TradingViewUtil.watchlistSymbol(url)).isEqualTo("BINANCE:BTCUSDT");
    }

    @Test
    void watchlistSymbolMatchesUrlUsdtAppending() {
        // generateUrl appends USDT; the watchlist token must match
        String url = TradingViewUtil.generateUrl(MarketProvider.BINANCE, "ETH", Timeframe.W1);
        assertThat(TradingViewUtil.watchlistSymbol(url)).isEqualTo("BINANCE:ETHUSDT");
    }

    @Test
    void watchlistSymbolIsNullForNullUrl() {
        assertThat(TradingViewUtil.watchlistSymbol(null)).isNull();
    }

    @Test
    void watchlistSymbolIsNullWhenNoSymbolParam() {
        assertThat(TradingViewUtil.watchlistSymbol("https://www.tradingview.com/chart/")).isNull();
    }
}
