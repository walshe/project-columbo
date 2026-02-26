package walshe.projectcolumbo.marketdata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BinanceMarketDataProviderTest {

    private BinanceMarketDataProvider provider;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        BinanceProperties properties = new BinanceProperties("https://api.binance.com");
        provider = new BinanceMarketDataProvider(builder, properties);
    }

    @Test
    void shouldFetchAndParseDailyCandles() {
        // Given
        String symbol = "BTC/USDT";
        String jsonResponse = """
            [
              [
                1708387200000,
                "52000.10",
                "53000.20",
                "51000.30",
                "52500.40",
                "100.50",
                1708473599999,
                "5250000.00",
                1000,
                "50.25",
                "2625000.00",
                "0"
              ]
            ]
            """;

        server.expect(requestTo("https://api.binance.com/api/v3/klines?symbol=BTCUSDT&interval=1d"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // When
        List<CandleDto> candles = provider.fetchDailyCandles(symbol, null, null);

        // Then
        assertThat(candles).hasSize(1);
        CandleDto candle = candles.get(0);
        assertThat(candle.open()).isEqualByComparingTo("52000.10");
        assertThat(candle.high()).isEqualByComparingTo("53000.20");
        assertThat(candle.low()).isEqualByComparingTo("51000.30");
        assertThat(candle.close()).isEqualByComparingTo("52500.40");
        assertThat(candle.volume()).isEqualByComparingTo("100.50");
        assertThat(candle.openTime()).isEqualTo(Instant.ofEpochMilli(1708387200000L));
        assertThat(candle.closeTime()).isEqualTo(Instant.ofEpochMilli(1708473599999L));
    }

    @Test
    void shouldFetchWithTimeWindow() {
        // Given
        String symbol = "BTC/USDT";
        Long startTime = 1708387200000L;
        Long endTime = 1708473599999L;
        String jsonResponse = "[]";

        server.expect(requestTo("https://api.binance.com/api/v3/klines?symbol=BTCUSDT&interval=1d&startTime=1708387200000&endTime=1708473599999"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // When
        provider.fetchDailyCandles(symbol, startTime, endTime);

        // Then
        server.verify();
    }
}
