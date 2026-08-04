package walshe.projectcolumbo.supertrend.ingestion;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BinanceMarketDataProviderTest {

    private final BinanceMarketDataProvider provider = new BinanceMarketDataProvider();

    @Test
    void parsesKlineRowsIntoD1Candles() {
        // One row: [openTime, open, high, low, close, baseVolume, closeTime, quoteVolume, trades, ...]
        String json = """
                [
                  [1735689600000, "42000.00", "43500.50", "41800.25", "43200.75", "1200.5", 1735775999999, "51500000.12", 308, "0", "0", "0"]
                ]
                """;

        List<Candle> candles = provider.parseKlines(json);

        assertThat(candles).hasSize(1);
        Candle candle = candles.get(0);
        assertThat(candle.timeframe()).isEqualTo(Timeframe.D1);
        assertThat(candle.openTime()).isEqualTo(Instant.ofEpochMilli(1735689600000L).atOffset(ZoneOffset.UTC));
        assertThat(candle.closeTime()).isEqualTo(Instant.ofEpochMilli(1735775999999L).atOffset(ZoneOffset.UTC));
        assertThat(candle.open()).isEqualByComparingTo("42000.00");
        assertThat(candle.high()).isEqualByComparingTo("43500.50");
        assertThat(candle.low()).isEqualByComparingTo("41800.25");
        assertThat(candle.close()).isEqualByComparingTo("43200.75");
    }

    @Test
    void volumeIsReadFromQuoteAssetVolumeNotBaseAssetVolume() {
        // index 5 = base-asset volume ("1200.5"), index 7 = quote-asset volume ("51500000.12")
        // — the candle's volume must come from index 7, not index 5.
        String json = """
                [
                  [1735689600000, "42000.00", "43500.50", "41800.25", "43200.75", "1200.5", 1735775999999, "51500000.12", 308, "0", "0", "0"]
                ]
                """;

        Candle candle = provider.parseKlines(json).get(0);

        assertThat(candle.volume()).isEqualByComparingTo("51500000.12");
    }

    @Test
    void isInvalidSymbolResponseDetectsBinanceErrorCode() {
        String invalidSymbolBody = "{\"code\":-1121,\"msg\":\"Invalid symbol.\"}";

        assertThat(provider.isInvalidSymbolResponse(invalidSymbolBody)).isTrue();
    }

    @Test
    void isInvalidSymbolResponseFalseForOtherErrors() {
        String rateLimitBody = "{\"code\":-1003,\"msg\":\"Too many requests.\"}";

        assertThat(provider.isInvalidSymbolResponse(rateLimitBody)).isFalse();
    }

    @Test
    void isInvalidSymbolResponseFalseForUnparsableBody() {
        assertThat(provider.isInvalidSymbolResponse("not json")).isFalse();
    }

    @Test
    void normalizeSymbolAppendsUsdtWhenMissing() {
        assertThat(BinanceMarketDataProvider.normalizeSymbol("BTC")).isEqualTo("BTCUSDT");
    }

    @Test
    void normalizeSymbolLeavesAlreadySuffixedSymbolUnchanged() {
        assertThat(BinanceMarketDataProvider.normalizeSymbol("BTCUSDT")).isEqualTo("BTCUSDT");
    }

    @Test
    void normalizeSymbolStripsSlashesAndDashes() {
        assertThat(BinanceMarketDataProvider.normalizeSymbol("btc/usdt")).isEqualTo("BTCUSDT");
        assertThat(BinanceMarketDataProvider.normalizeSymbol("btc-usdt")).isEqualTo("BTCUSDT");
    }

    @Test
    void klinesUriStripsTrailingSlashFromConfiguredBaseUrlToAvoidADoubleSlash() {
        BinanceMarketDataProvider withTrailingSlash =
                new BinanceMarketDataProvider(HttpClient.newHttpClient(), "http://binance-stub:8080/");

        URI uri = withTrailingSlash.klinesUri("BTCUSDT", 1000L, 2000L);

        assertThat(uri).isEqualTo(URI.create("http://binance-stub:8080/api/v3/klines?symbol=BTCUSDT&interval=1d&startTime=1000&endTime=2000"));
    }

    @Test
    void klinesUriStripsMultipleTrailingSlashes() {
        BinanceMarketDataProvider withTrailingSlashes =
                new BinanceMarketDataProvider(HttpClient.newHttpClient(), "http://binance-stub:8080///");

        URI uri = withTrailingSlashes.klinesUri("BTCUSDT", 1000L, 2000L);

        assertThat(uri).isEqualTo(URI.create("http://binance-stub:8080/api/v3/klines?symbol=BTCUSDT&interval=1d&startTime=1000&endTime=2000"));
    }

    @Test
    void klinesUriIsUnaffectedWhenBaseUrlHasNoTrailingSlash() {
        assertThat(provider.klinesUri("BTCUSDT", 1000L, 2000L))
                .isEqualTo(URI.create("https://api.binance.com/api/v3/klines?symbol=BTCUSDT&interval=1d&startTime=1000&endTime=2000"));
    }
}
