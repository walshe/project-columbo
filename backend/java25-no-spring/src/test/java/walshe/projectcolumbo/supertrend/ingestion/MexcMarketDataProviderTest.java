package walshe.projectcolumbo.supertrend.ingestion;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MexcMarketDataProviderTest {

    private final MexcMarketDataProvider provider = new MexcMarketDataProvider(HttpClient.newHttpClient());

    @Test
    void parsesKlineRowsIntoD1Candles() {
        // One row: [openTime, open, high, low, close, baseVolume, closeTime, quoteVolume]
        String json = """
                [
                  [1735689600000, "42000.00", "43500.50", "41800.25", "43200.75", "1200.5", 1735775999999, "51500000.12"]
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
    void closeTimeIsNormalizedWhenMexcReportsTheNextBarsOpenAsClose() {
        // Live MEXC shape (verified 2026-10-09): close time [6] == next bar's open (open + 86,400,000).
        String json = """
                [
                  [1762905600000, "1", "2", "0.5", "1.5", "10", 1762992000000, "15"]
                ]
                """;

        Candle candle = provider.parseKlines(json).getFirst();

        assertThat(candle.openTime()).isEqualTo(OffsetDateTime.parse("2025-11-12T00:00:00Z"));
        assertThat(candle.closeTime()).isEqualTo(OffsetDateTime.parse("2025-11-12T23:59:59.999Z"));
        assertThat(Timeframe.D1.openTimeFor(candle.closeTime())).isEqualTo(candle.openTime());
    }

    @Test
    void volumeIsReadFromQuoteAssetVolumeNotBaseAssetVolume() {
        String json = """
                [
                  [1735689600000, "42000.00", "43500.50", "41800.25", "43200.75", "1200.5", 1735775999999, "51500000.12"]
                ]
                """;

        Candle candle = provider.parseKlines(json).get(0);

        assertThat(candle.volume()).isEqualByComparingTo("51500000.12");
    }

    @Test
    void skipsEmpty500DayWindowsBeforeASymbolsListingInsteadOfReportingNoData() throws Exception {
        // Stub mimicking live MEXC (verified 2026-10-09): only bars in [startTime, startTime + 500d)
        // are returned, so a start 600 days before listing yields an empty first window.
        long listingMs = OffsetDateTime.parse("2026-01-15T00:00:00Z").toInstant().toEpochMilli();
        long dayMs = Duration.ofDays(1).toMillis();
        List<Long> requestedStarts = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v3/klines", exchange -> {
            Map<String, Long> query = new HashMap<>();
            for (String pair : exchange.getRequestURI().getQuery().split("&")) {
                String[] kv = pair.split("=");
                if (kv[0].endsWith("Time")) {
                    query.put(kv[0], Long.parseLong(kv[1]));
                }
            }
            long start = query.get("startTime");
            requestedStarts.add(start);
            long windowEnd = Math.min(start + MexcMarketDataProvider.MAX_WINDOW_MS, query.get("endTime"));
            StringBuilder body = new StringBuilder("[");
            for (long open = listingMs; open < windowEnd && open < listingMs + 3 * dayMs; open += dayMs) {
                if (open >= start) {
                    body.append(body.length() > 1 ? "," : "")
                            .append("[").append(open).append(",\"1\",\"2\",\"0.5\",\"1.5\",\"10\",").append(open + dayMs).append(",\"15\"]");
                }
            }
            byte[] bytes = body.append("]").toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            MexcMarketDataProvider stubbed = new MexcMarketDataProvider(HttpClient.newHttpClient(),
                    "http://localhost:" + server.getAddress().getPort());
            long start = listingMs - Duration.ofDays(600).toMillis();
            long end = listingMs + Duration.ofDays(10).toMillis();

            List<Candle> candles = stubbed.fetchDailyCandles("NEWONUSDT", start, end);

            assertThat(candles).hasSize(3);
            assertThat(candles.getFirst().openTime()).isEqualTo(OffsetDateTime.parse("2026-01-15T00:00:00Z"));
            assertThat(requestedStarts).containsExactly(start, start + MexcMarketDataProvider.MAX_WINDOW_MS);

            // A genuinely empty range still terminates (window reaches endTime) and returns empty.
            assertThat(stubbed.fetchDailyCandles("NEWONUSDT", start, listingMs)).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void parseKlinesRejectsANonArrayBodyWithAMarketDataProviderException() {
        String errorObject = "{\"code\":-1003,\"msg\":\"Too many requests.\"}";

        assertThatThrownBy(() -> provider.parseKlines(errorObject))
                .isInstanceOf(MarketDataProviderException.class)
                .hasMessageContaining("not a JSON array");
    }

    @Test
    void isInvalidSymbolResponseDetectsMexcErrorCode() {
        String invalidSymbolBody = "{\"msg\":\"Invalid symbol.\",\"code\":-1121,\"_extend\":null}";

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
    void klinesUriStripsTrailingSlashFromConfiguredBaseUrlToAvoidADoubleSlash() {
        MexcMarketDataProvider withTrailingSlash =
                new MexcMarketDataProvider(HttpClient.newHttpClient(), "http://mexc-stub:8080/");

        URI uri = withTrailingSlash.klinesUri("BTCUSDT", 1000L, 2000L);

        assertThat(uri).isEqualTo(URI.create("http://mexc-stub:8080/api/v3/klines?symbol=BTCUSDT&interval=1d&startTime=1000&endTime=2000"));
    }

    @Test
    void klinesUriStripsMultipleTrailingSlashes() {
        MexcMarketDataProvider withTrailingSlashes =
                new MexcMarketDataProvider(HttpClient.newHttpClient(), "http://mexc-stub:8080///");

        URI uri = withTrailingSlashes.klinesUri("BTCUSDT", 1000L, 2000L);

        assertThat(uri).isEqualTo(URI.create("http://mexc-stub:8080/api/v3/klines?symbol=BTCUSDT&interval=1d&startTime=1000&endTime=2000"));
    }

    @Test
    void klinesUriIsUnaffectedWhenBaseUrlHasNoTrailingSlash() {
        assertThat(provider.klinesUri("AAPLONUSDT", 1000L, 2000L))
                .isEqualTo(URI.create("https://api.mexc.com/api/v3/klines?symbol=AAPLONUSDT&interval=1d&startTime=1000&endTime=2000"));
    }

    @Test
    void defaultBaseUrlIsMexcSpotApi() {
        assertThat(provider.klinesUri("BTCUSDT", 1000L, 2000L))
                .isEqualTo(URI.create("https://api.mexc.com/api/v3/klines?symbol=BTCUSDT&interval=1d&startTime=1000&endTime=2000"));
    }
}
