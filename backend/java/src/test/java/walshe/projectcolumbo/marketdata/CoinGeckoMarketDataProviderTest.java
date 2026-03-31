package walshe.projectcolumbo.marketdata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CoinGeckoMarketDataProviderTest {

    private CoinGeckoMarketDataProvider provider;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        CoinGeckoProperties properties = new CoinGeckoProperties(null, "https://api.coingecko.com/api/v3");
        provider = new CoinGeckoMarketDataProvider(builder, properties);
    }

    @Test
    void shouldFetchAndParseDailyCandles() {
        // Given
        String providerId = "bitcoin";
        String jsonResponse = """
            [
              [1708425600000, 52000.1, 53000.2, 51000.3, 52500.4],
              [1708512000000, 52500.5, 54000.6, 52000.7, 53500.8]
            ]
            """;

        server.expect(requestTo("https://api.coingecko.com/api/v3/coins/bitcoin/ohlc?vs_currency=usd&days=365"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // When
        List<CandleDto> candles = provider.fetchDailyCandles(providerId, null, null);

        // Then
        assertThat(candles).hasSize(2);

        CandleDto first = candles.get(0);
        assertThat(first.open()).isEqualByComparingTo("52000.1");
        assertThat(first.high()).isEqualByComparingTo("53000.2");
        assertThat(first.low()).isEqualByComparingTo("51000.3");
        assertThat(first.close()).isEqualByComparingTo("52500.4");
        assertThat(first.volume()).isNull();
        
        Instant expectedCloseTime = Instant.ofEpochMilli(1708425600000L);
        assertThat(first.closeTime()).isEqualTo(expectedCloseTime);
        assertThat(first.openTime()).isEqualTo(expectedCloseTime.minus(1, ChronoUnit.DAYS));
    }

    @Test
    void shouldReturnEmptyListWhenResponseIsNull() {
        // Given
        String providerId = "bitcoin";
        server.expect(requestTo("https://api.coingecko.com/api/v3/coins/bitcoin/ohlc?vs_currency=usd&days=365"))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        // When
        List<CandleDto> candles = provider.fetchDailyCandles(providerId, null, null);

        // Then
        assertThat(candles).isEmpty();
    }
}
