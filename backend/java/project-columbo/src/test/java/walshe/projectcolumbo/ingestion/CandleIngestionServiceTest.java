package walshe.projectcolumbo.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
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

import static org.assertj.core.api.Assertions.assertThat;
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
    @Mock
    private CandlePersistenceService candlePersistenceService;

    private CandleIngestionService candleIngestionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(binanceProvider.getProviderName()).thenReturn("BINANCE");
        IngestionProperties ingestionProperties = new IngestionProperties(OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        candleIngestionService = new CandleIngestionService(assetRepository, candleRepository, List.of(binanceProvider), orchestrator, ingestionProperties, candlePersistenceService);
    }

    @Test
    void ingestDaily_shouldDelegateToPersistenceService() {
        // Given
        Asset btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        when(assetRepository.findByActiveTrue()).thenReturn(List.of(btc));

        Instant now = Instant.now();
        Instant todayUtcStart = now.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
        Instant yesterday = todayUtcStart.minus(1, ChronoUnit.DAYS);

        CandleDto finalizedCandle = new CandleDto(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("5"), BigDecimal.valueOf(100), yesterday, yesterday.plus(1, ChronoUnit.DAYS).minusMillis(1));

        when(binanceProvider.fetchDailyCandles(eq("BTCUSDT"), any(), any())).thenReturn(List.of(finalizedCandle));
        when(candlePersistenceService.persistCandles(any(), any(), any(), any())).thenReturn(new CandleIngestionService.IngestionStats());

        // When
        candleIngestionService.ingestDaily();

        // Then
        verify(candlePersistenceService).persistCandles(eq(btc), eq(List.of(finalizedCandle)), any(Instant.class), any());
    }

    @Test
    void ingestDaily_shouldHandleProviderNotFound() {
        // Given
        Asset btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        when(assetRepository.findByActiveTrue()).thenReturn(List.of(btc));
        
        // Use a service without providers
        IngestionProperties ingestionProperties = new IngestionProperties(OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        CandleIngestionService serviceNoProviders = new CandleIngestionService(assetRepository, candleRepository, List.of(), orchestrator, ingestionProperties, candlePersistenceService);

        // When
        serviceNoProviders.ingestDaily();

        // Then
        verify(candlePersistenceService, never()).persistCandles(any(), any(), any(), any());
    }

    @Test
    void ingestDaily_shouldDetectRevisions() {
        // This test logic is now handled in CandlePersistenceService.
        // We just verify it's called.
        // Given
        Asset btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        when(assetRepository.findByActiveTrue()).thenReturn(List.of(btc));

        CandleDto dto = new CandleDto(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("6"), BigDecimal.valueOf(100), Instant.now().minus(1, ChronoUnit.DAYS), Instant.now());
        when(binanceProvider.fetchDailyCandles(eq("BTCUSDT"), any(), any())).thenReturn(List.of(dto));

        // When
        candleIngestionService.ingestDaily();

        // Then
        verify(candlePersistenceService).persistCandles(eq(btc), any(), any(), any());
    }

    @Test
    void ingestForAsset_shouldComputeStartTimeFromLastClosePlusOneMs() {
        // Given
        Asset btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        btc.setId(1L);
        when(assetRepository.findByActiveTrue()).thenReturn(List.of(btc));

        Instant lastCloseInstant = Instant.parse("2024-01-01T23:59:59.999Z");
        OffsetDateTime lastClose = OffsetDateTime.ofInstant(lastCloseInstant, ZoneOffset.UTC);
        when(candleRepository.findLatestCloseTime(1L, "D1")).thenReturn(Optional.of(lastClose));

        when(binanceProvider.fetchDailyCandles(any(), any(), any())).thenReturn(List.of());

        // When
        candleIngestionService.ingestDaily();

        // Then
        // startTime should be lastClose + 1ms = 2024-01-02T00:00:00.000Z
        long expectedStartTime = lastCloseInstant.toEpochMilli() + 1;
        verify(binanceProvider).fetchDailyCandles(eq("BTCUSDT"), eq(expectedStartTime), any());
    }

    @Test
    void ingestForAsset_shouldComputeStartTimeFromBackfillStartWhenNoLastClose() {
        // Given
        Asset btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        btc.setId(1L);
        when(assetRepository.findByActiveTrue()).thenReturn(List.of(btc));

        when(candleRepository.findLatestCloseTime(1L, "D1")).thenReturn(Optional.empty());
        when(binanceProvider.fetchDailyCandles(any(), any(), any())).thenReturn(List.of());

        // When
        candleIngestionService.ingestDaily();

        // Then
        // backfillStart is 2020-01-01T00:00:00Z
        long expectedStartTime = OffsetDateTime.parse("2020-01-01T00:00:00Z").toInstant().toEpochMilli();
        verify(binanceProvider).fetchDailyCandles(eq("BTCUSDT"), eq(expectedStartTime), any());
    }

    @Test
    void ingestForAsset_shouldSkipWhenStartTimeIsGreaterOrEqualToEndTime() {
        // Given
        Asset btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        btc.setId(1L);
        when(assetRepository.findByActiveTrue()).thenReturn(List.of(btc));

        // Set lastClose to today (so startTime will be after finalizedBoundary)
        Instant todayUtcStart = Instant.now().atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
        OffsetDateTime lastClose = OffsetDateTime.ofInstant(todayUtcStart, ZoneOffset.UTC);
        when(candleRepository.findLatestCloseTime(1L, "D1")).thenReturn(Optional.of(lastClose));

        // When
        candleIngestionService.ingestDaily();

        // Then
        verify(binanceProvider, never()).fetchDailyCandles(any(), any(), any());
    }

    @Test
    void scheduledIngest_shouldCatchExceptions() {
        // Given
        when(orchestrator.runInternal(any(), any())).thenThrow(new RuntimeException("DB error"));

        // When/Then (should not throw exception)
        candleIngestionService.scheduledIngest();

        verify(orchestrator).runInternal(any(), any());
    }

    @Test
    void ingestForAsset_shouldMarkAssetInactiveOnBinanceInvalidSymbolError() {
        // Given
        Asset cro = new Asset("CRO", "Cronos", MarketProvider.BINANCE, true);
        cro.setId(10L);
        when(assetRepository.findByActiveTrue()).thenReturn(List.of(cro));

        // Mock Binance throwing 400 Bad Request with -1121 code in body
        String errorBody = "{\"code\":-1121,\"msg\":\"Invalid symbol.\"}";
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, 
                "Bad Request", 
                null, 
                errorBody.getBytes(), 
                null
        );
        
        when(binanceProvider.fetchDailyCandles(any(), any(), any())).thenThrow(exception);

        // When
        CandleIngestionService.IngestionStats stats = candleIngestionService.ingestDaily();

        // Then
        assertThat(cro.isActive()).isFalse();
        verify(assetRepository).save(cro);
        assertThat(stats.errorCount).isEqualTo(1);
        assertThat(stats.firstErrorMessage).contains("Invalid symbol: CRO");
    }
}
