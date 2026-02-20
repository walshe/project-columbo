package walshe.projectcolumbo.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import walshe.projectcolumbo.marketdata.CandleDto;
import walshe.projectcolumbo.marketdata.MarketDataProvider;
import walshe.projectcolumbo.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class CandleIngestionServiceTest {

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private CandleRepository candleRepository;
    @Mock
    private MarketDataProvider binanceProvider;

    private CandleIngestionService candleIngestionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(binanceProvider.getProviderName()).thenReturn("BINANCE");
        candleIngestionService = new CandleIngestionService(assetRepository, candleRepository, List.of(binanceProvider));
    }

    @Test
    void ingestDaily_shouldFilterNonFinalizedCandles() {
        // Given
        Asset btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        when(assetRepository.findByActiveTrue()).thenReturn(List.of(btc));

        Instant now = Instant.now();
        Instant todayUtcStart = now.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
        Instant yesterday = todayUtcStart.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = todayUtcStart.minus(2, ChronoUnit.DAYS);

        CandleDto oldCandle = new CandleDto(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("5"), BigDecimal.valueOf(100), twoDaysAgo, twoDaysAgo.plus(1, ChronoUnit.DAYS).minusMillis(1));
        CandleDto finalizedCandle = new CandleDto(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("5"), BigDecimal.valueOf(100), yesterday, yesterday.plus(1, ChronoUnit.DAYS).minusMillis(1));
        CandleDto nonFinalizedCandle = new CandleDto(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("5"), BigDecimal.valueOf(100), todayUtcStart, now);

        when(binanceProvider.fetchDailyCandles("BTCUSDT")).thenReturn(List.of(oldCandle, finalizedCandle, nonFinalizedCandle));

        // When
        candleIngestionService.ingestDaily();

        // Then
        verify(candleRepository, times(1)).saveAll(argThat(list -> {
            List<Candle> candles = (List<Candle>) list;
            return candles.size() == 2; // only old and finalized
        }));
    }

    @Test
    void ingestDaily_shouldHandleProviderNotFound() {
        // Given
        Asset btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        when(assetRepository.findByActiveTrue()).thenReturn(List.of(btc));
        
        // Use a service without providers
        CandleIngestionService serviceNoProviders = new CandleIngestionService(assetRepository, candleRepository, List.of());

        // When
        serviceNoProviders.ingestDaily();

        // Then
        verify(candleRepository, never()).saveAll(anyList());
    }
}
