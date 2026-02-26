package walshe.projectcolumbo.marketdata;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import walshe.projectcolumbo.TestcontainersConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@Disabled("Disabled for now as it makes real network calls or needs WireMock")
class CoinGeckoMarketDataProviderIT {

    @Autowired
    private CoinGeckoMarketDataProvider provider;

    @Test
    void shouldFetchRealData() {
        // This test would hit the real CoinGecko API if enabled
        List<CandleDto> candles = provider.fetchDailyCandles("bitcoin", null, null);
        assertThat(candles).isNotEmpty();
    }
}
