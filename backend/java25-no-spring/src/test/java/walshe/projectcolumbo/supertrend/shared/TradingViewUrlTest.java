package walshe.projectcolumbo.supertrend.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TradingViewUrlTest {

    @Test
    void generateUrlAppendsUsdtWhenMissing() {
        String url = TradingViewUrl.generateUrl(Provider.BINANCE, "ETH", Timeframe.D1);
        assertThat(url).isEqualTo("https://www.tradingview.com/chart/?symbol=BINANCE%3AETHUSDT&interval=1D");
    }

    @Test
    void generateUrlDoesNotDoubleAppendUsdt() {
        String url = TradingViewUrl.generateUrl(Provider.BINANCE, "BTCUSDT", Timeframe.W1);
        assertThat(url).isEqualTo("https://www.tradingview.com/chart/?symbol=BINANCE%3ABTCUSDT&interval=1W");
    }

    @Test
    void generateUrlIsNullWhenAnyArgumentIsNull() {
        assertThat(TradingViewUrl.generateUrl(null, "BTCUSDT", Timeframe.D1)).isNull();
        assertThat(TradingViewUrl.generateUrl(Provider.BINANCE, null, Timeframe.D1)).isNull();
        assertThat(TradingViewUrl.generateUrl(Provider.BINANCE, "BTCUSDT", null)).isNull();
    }

    @Test
    void watchlistSymbolRoundTripsFromGeneratedUrl() {
        String url = TradingViewUrl.generateUrl(Provider.BINANCE, "BTCUSDT", Timeframe.D1);
        assertThat(TradingViewUrl.watchlistSymbol(url)).isEqualTo("BINANCE:BTCUSDT");
    }

    @Test
    void watchlistSymbolMatchesUrlUsdtAppending() {
        String url = TradingViewUrl.generateUrl(Provider.BINANCE, "ETH", Timeframe.W1);
        assertThat(TradingViewUrl.watchlistSymbol(url)).isEqualTo("BINANCE:ETHUSDT");
    }

    @Test
    void watchlistSymbolIsNullForNullUrl() {
        assertThat(TradingViewUrl.watchlistSymbol(null)).isNull();
    }

    @Test
    void watchlistSymbolIsNullWhenNoSymbolParam() {
        assertThat(TradingViewUrl.watchlistSymbol("https://www.tradingview.com/chart/")).isNull();
    }

    @Test
    void watchlistSymbolIgnoresTrailingFragment() {
        // A trailing #fragment (even one containing "symbol=") must not confuse the query parse.
        String url = "https://www.tradingview.com/chart/?symbol=BINANCE%3ABTCUSDT&interval=1D#symbol=EVIL";
        assertThat(TradingViewUrl.watchlistSymbol(url)).isEqualTo("BINANCE:BTCUSDT");
    }

    @Test
    void watchlistSymbolHandlesEncodedAmpersandInValue() {
        // An encoded & inside the symbol value must not be treated as a param separator.
        String url = "https://www.tradingview.com/chart/?symbol=FOO%26BAR%3ABTCUSDT&interval=1D";
        assertThat(TradingViewUrl.watchlistSymbol(url)).isEqualTo("FOO&BAR:BTCUSDT");
    }

    @Test
    void watchlistSymbolIsNullForMalformedUrl() {
        assertThat(TradingViewUrl.watchlistSymbol("ht tp://bad url")).isNull();
    }
}
