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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CandleIngestionServiceTest {

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private CandleRepository candleRepository;
    @Mock
    private MarketDataProvider binanceProvider;
    @Mock
    private IngestionOrchestrator orchestrator;

    private CandleIngestionService candleIngestionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(binanceProvider.getProviderName()).thenReturn("BINANCE");
        candleIngestionService = new CandleIngestionService(assetRepository, candleRepository, List.of(binanceProvider), orchestrator);
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

        when(binanceProvider.fetchDailyCandles(eq("BTCUSDT"), any(), any())).thenReturn(List.of(oldCandle, finalizedCandle, nonFinalizedCandle));

        // When
        candleIngestionService.ingestDaily();

        // Then
        verify(candleRepository, times(2)).save(any(Candle.class));
    }

    @Test
    void ingestDaily_shouldHandleProviderNotFound() {
        // Given
        Asset btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        when(assetRepository.findByActiveTrue()).thenReturn(List.of(btc));
        
        // Use a service without providers
        CandleIngestionService serviceNoProviders = new CandleIngestionService(assetRepository, candleRepository, List.of(), orchestrator);

        // When
        serviceNoProviders.ingestDaily();

        // Then
        verify(candleRepository, never()).save(any(Candle.class));
    }

    @Test
    void ingestDaily_shouldDetectRevisions() {
        // Given
        Asset btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        when(assetRepository.findByActiveTrue()).thenReturn(List.of(btc));

        Instant yesterday = Instant.now().atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant().minus(1, ChronoUnit.DAYS);
        OffsetDateTime yesterdayOd = OffsetDateTime.ofInstant(yesterday, ZoneOffset.UTC);

        CandleDto dto = new CandleDto(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("6"), BigDecimal.valueOf(100), yesterday.minus(1, ChronoUnit.DAYS), yesterday);
        
        Candle existing = new Candle();
        existing.setAsset(btc);
        existing.setTimeframe(Timeframe.D1);
        existing.setCloseTime(yesterdayOd);
        existing.setClose(new BigDecimal("5")); // Different from DTO
        existing.setOpen(BigDecimal.ONE);
        existing.setHigh(BigDecimal.TEN);
        existing.setLow(BigDecimal.ONE);
        existing.setVolume(BigDecimal.valueOf(100));
        existing.setSource(MarketProvider.BINANCE);

        when(binanceProvider.fetchDailyCandles(eq("BTCUSDT"), any(), any())).thenReturn(List.of(dto));
        when(candleRepository.findByAssetAndTimeframeAndCloseTime(eq(btc), eq(Timeframe.D1), eq(yesterdayOd)))
                .thenReturn(Optional.of(existing));

        // When
        candleIngestionService.ingestDaily();

        // Then
        verify(candleRepository, times(1)).save(existing);
        assert existing.getClose().compareTo(new BigDecimal("6")) == 0;
    }

    @Test
    void scheduledIngest_shouldCatchExceptions() {
        // Given
        when(orchestrator.runInternal(any(), any())).thenThrow(new RuntimeException("DB error"));

        // When/Then (should not throw exception)
        candleIngestionService.scheduledIngest();

        verify(orchestrator).runInternal(any(), any());
    }
}
