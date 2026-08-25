package walshe.projectcolumbo.supertrend.ingestion;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TiingoMarketDataProviderTest {

    private final TiingoMarketDataProvider provider = new TiingoMarketDataProvider(HttpClient.newHttpClient(), "test-key");

    @Test
    void parsesDailyPricesIntoD1CandlesUsingAdjustedFields() {
        String json = """
                [
                  {
                    "date": "2024-01-02T00:00:00.000Z",
                    "open": 100.0, "high": 110.0, "low": 90.0, "close": 105.0, "volume": 1000,
                    "adjOpen": 50.0, "adjHigh": 55.0, "adjLow": 45.0, "adjClose": 52.5, "adjVolume": 2000,
                    "divCash": 0.0, "splitFactor": 2.0
                  }
                ]
                """;

        List<Candle> candles = provider.parseDailyPrices(json);

        assertThat(candles).hasSize(1);
        Candle candle = candles.get(0);
        assertThat(candle.timeframe()).isEqualTo(Timeframe.D1);
        assertThat(candle.openTime()).isEqualTo(OffsetDateTime.parse("2024-01-02T00:00:00.000Z"));
        assertThat(candle.closeTime()).isEqualTo(OffsetDateTime.parse("2024-01-02T00:00:00.000Z").plusDays(1).minusNanos(1_000_000));
        assertThat(candle.open()).isEqualByComparingTo("50.0");
        assertThat(candle.high()).isEqualByComparingTo("55.0");
        assertThat(candle.low()).isEqualByComparingTo("45.0");
        assertThat(candle.close()).isEqualByComparingTo("52.5");
        assertThat(candle.volume()).isEqualByComparingTo("2000");
    }

    @Test
    void toCalendarDateTruncatesEpochMillisToUtcCalendarDate() {
        long epochMs = Instant.parse("2024-06-15T13:45:00Z").toEpochMilli();

        assertThat(TiingoMarketDataProvider.toCalendarDate(epochMs)).isEqualTo("2024-06-15");
    }

    @Test
    void pricesUriUsesStartDateAsIsAndEndDateAsTheDayBeforeTheExclusiveBoundary() {
        long startTimeMs = Instant.parse("2024-01-02T00:00:00Z").toEpochMilli();
        long endTimeMs = Instant.parse("2024-01-10T00:00:00Z").toEpochMilli(); // exclusive boundary

        URI uri = provider.pricesUri("AAPL", startTimeMs, endTimeMs);

        assertThat(uri).isEqualTo(URI.create(
                "https://api.tiingo.com/tiingo/daily/AAPL/prices?startDate=2024-01-02&endDate=2024-01-09&token=test-key"));
    }

    @Test
    void pricesUriStripsTrailingSlashFromConfiguredBaseUrlToAvoidADoubleSlash() {
        TiingoMarketDataProvider withTrailingSlash =
                new TiingoMarketDataProvider(HttpClient.newHttpClient(), "test-key", "http://tiingo-stub:8080/");

        URI uri = withTrailingSlash.pricesUri("AAPL", 1000L, 86_400_000L);

        assertThat(uri.toString()).startsWith("http://tiingo-stub:8080/tiingo/daily/AAPL/prices?");
    }

    @Test
    void defaultsToTheRealTiingoBaseUrl() {
        URI uri = provider.pricesUri("AAPL", 1000L, 86_400_000L);

        assertThat(uri.toString()).startsWith("https://api.tiingo.com/tiingo/daily/AAPL/prices?");
    }

    @Test
    void handleErrorResponseThrowsInvalidSymbolExceptionOn404() {
        assertThatThrownBy(() -> provider.handleErrorResponse("BADSYMBOL", fakeResponse(404, "{\"detail\":\"Not found.\"}")))
                .isInstanceOf(InvalidSymbolException.class);
    }

    @Test
    void handleErrorResponseThrowsMarketDataProviderExceptionOnOtherErrors() {
        assertThatThrownBy(() -> provider.handleErrorResponse("AAPL", fakeResponse(429, "{\"detail\":\"Too many requests\"}")))
                .isInstanceOf(MarketDataProviderException.class);
    }

    @Test
    void constructorRejectsBlankApiKey() {
        assertThatThrownBy(() -> new TiingoMarketDataProvider(HttpClient.newHttpClient(), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorRejectsNullApiKey() {
        assertThatThrownBy(() -> new TiingoMarketDataProvider(HttpClient.newHttpClient(), null))
                .isInstanceOf(NullPointerException.class);
    }

    private static HttpResponse<String> fakeResponse(int statusCode, String body) {
        return new HttpResponse<>() {
            @Override public int statusCode() { return statusCode; }
            @Override public HttpRequest request() { return null; }
            @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
            @Override public String body() { return body; }
            @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
            @Override public URI uri() { return URI.create("https://api.tiingo.com"); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
}
