package walshe.projectcolumbo.supertrend.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TradingViewUrlTest {

    @Test
    void generateUrlAppendsUsdtWhenMissing() {
        String url = TradingViewUrl.generateUrl(Provider.BINANCE, "ETH", Timeframe.D1, AssetVenue.SPOT, null);
        assertThat(url).isEqualTo("https://www.tradingview.com/chart/?symbol=BINANCE%3AETHUSDT&interval=1D");
    }

    @Test
    void generateUrlDoesNotDoubleAppendUsdt() {
        String url = TradingViewUrl.generateUrl(Provider.BINANCE, "BTCUSDT", Timeframe.W1, AssetVenue.SPOT, null);
        assertThat(url).isEqualTo("https://www.tradingview.com/chart/?symbol=BINANCE%3ABTCUSDT&interval=1W");
    }

    @Test
    void generateUrlAppendsDotPForFuturesVenue() {
        String url = TradingViewUrl.generateUrl(Provider.BINANCE, "BITOUSDT", Timeframe.D1, AssetVenue.FUTURES, null);
        assertThat(url).isEqualTo("https://www.tradingview.com/chart/?symbol=BINANCE%3ABITOUSDT.P&interval=1D");
    }

    @Test
    void generateUrlAppendsUsdtThenDotPForFuturesVenueWhenUsdtIsMissing() {
        String url = TradingViewUrl.generateUrl(Provider.BINANCE, "BITO", Timeframe.D1, AssetVenue.FUTURES, null);
        assertThat(url).isEqualTo("https://www.tradingview.com/chart/?symbol=BINANCE%3ABITOUSDT.P&interval=1D");
    }

    @Test
    void generateUrlIsNullWhenAnyArgumentIsNull() {
        assertThat(TradingViewUrl.generateUrl(null, "BTCUSDT", Timeframe.D1, AssetVenue.SPOT, null)).isNull();
        assertThat(TradingViewUrl.generateUrl(Provider.BINANCE, null, Timeframe.D1, AssetVenue.SPOT, null)).isNull();
        assertThat(TradingViewUrl.generateUrl(Provider.BINANCE, "BTCUSDT", null, AssetVenue.SPOT, null)).isNull();
        assertThat(TradingViewUrl.generateUrl(Provider.BINANCE, "BTCUSDT", Timeframe.D1, null, null)).isNull();
    }

    @Test
    void generateUrlIsNullForExchangeVenueWithoutAVerifiedRef() {
        // No fabricated Binance-shaped link (e.g. TIINGO:AAPLUSDT) when no verified TradingView
        // ref is stored for the asset - a wrong link is worse than none.
        assertThat(TradingViewUrl.generateUrl(Provider.TIINGO, "AAPL", Timeframe.D1, AssetVenue.EXCHANGE, null)).isNull();
    }

    @Test
    void generateUrlIsNullForExchangeVenueWithABlankRef() {
        assertThat(TradingViewUrl.generateUrl(Provider.TIINGO, "AAPL", Timeframe.D1, AssetVenue.EXCHANGE, "  ")).isNull();
    }

    @Test
    void generateUrlUsesTheVerifiedTradingviewRefForExchangeVenue() {
        String url = TradingViewUrl.generateUrl(Provider.TIINGO, "AAPL", Timeframe.D1, AssetVenue.EXCHANGE, "NASDAQ:AAPL");
        assertThat(url).isEqualTo("https://www.tradingview.com/chart/?symbol=NASDAQ%3AAAPL&interval=1D");
    }

    @Test
    void generateUrlIgnoresTradingviewRefForNonExchangeVenues() {
        // tradingviewRef is EXCHANGE-venue-specific data; a SPOT/FUTURES asset builds its link
        // from provider/symbol/venue exactly as before, regardless of what's passed here.
        String url = TradingViewUrl.generateUrl(Provider.BINANCE, "BTCUSDT", Timeframe.D1, AssetVenue.SPOT, "NASDAQ:AAPL");
        assertThat(url).isEqualTo("https://www.tradingview.com/chart/?symbol=BINANCE%3ABTCUSDT&interval=1D");
    }

    @Test
    void watchlistSymbolRoundTripsFromGeneratedUrl() {
        String url = TradingViewUrl.generateUrl(Provider.BINANCE, "BTCUSDT", Timeframe.D1, AssetVenue.SPOT, null);
        assertThat(TradingViewUrl.watchlistSymbol(url)).isEqualTo("BINANCE:BTCUSDT");
    }

    @Test
    void watchlistSymbolMatchesUrlUsdtAppending() {
        String url = TradingViewUrl.generateUrl(Provider.BINANCE, "ETH", Timeframe.W1, AssetVenue.SPOT, null);
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
