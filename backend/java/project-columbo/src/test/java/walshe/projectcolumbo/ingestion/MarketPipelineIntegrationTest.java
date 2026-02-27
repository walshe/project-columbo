package walshe.projectcolumbo.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import walshe.projectcolumbo.TestcontainersConfiguration;
import walshe.projectcolumbo.marketdata.CandleDto;
import walshe.projectcolumbo.marketdata.MarketDataProvider;
import walshe.projectcolumbo.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class MarketPipelineIntegrationTest {

    @Autowired
    private MarketPipelineService marketPipelineService;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private CandleRepository candleRepository;

    @Autowired
    private SuperTrendRepository superTrendRepository;

    @Autowired
    private SignalStateRepository signalStateRepository;

    @Autowired
    private MarketBreadthSnapshotRepository marketBreadthSnapshotRepository;

    @Autowired
    private IngestionRunRepository ingestionRunRepository;

    @Autowired
    private RsiRepository rsiRepository;

    @MockitoBean(name = "binanceMarketDataProvider")
    private MarketDataProvider binanceProvider;

    @BeforeEach
    void setUp() {
        signalStateRepository.deleteAll();
        superTrendRepository.deleteAll();
        rsiRepository.deleteAll();
        candleRepository.deleteAll();
        marketBreadthSnapshotRepository.deleteAll();
        ingestionRunRepository.deleteAll();
        assetRepository.deleteAll();

        when(binanceProvider.getProviderName()).thenReturn("BINANCE");
    }

    @Test
    void shouldExecuteFullPipelineSuccessfully() {
        // 1. Seed assets
        Asset btc = assetRepository.save(new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true));
        
        // 2. Mock provider to return candles
        Instant now = Instant.now();
        Instant todayUtcStart = now.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
        
        // We need enough candles for SuperTrend (ATR 10)
        List<CandleDto> candles = new java.util.ArrayList<>();
        for (int i = 20; i >= 1; i--) {
            Instant start = todayUtcStart.minus(i, ChronoUnit.DAYS);
            candles.add(new CandleDto(
                new BigDecimal("40000"), new BigDecimal("41000"), new BigDecimal("39000"), new BigDecimal("40500"),
                new BigDecimal("1000"), start, start.plus(1, ChronoUnit.DAYS).minusMillis(1)
            ));
        }
        when(binanceProvider.fetchDailyCandles(eq("BTCUSDT"), any(), any())).thenReturn(candles);

        // 3. Run Pipeline
        marketPipelineService.runDaily(MarketProvider.BINANCE, Timeframe.D1, RunMode.INCREMENTAL);

        // 4. Verify results
        // Verify Ingestion
        assertTrue(candleRepository.count() > 0, "Candles should be ingested");
        
        // Verify Indicators
        assertTrue(superTrendRepository.count() > 0, "SuperTrend indicators should be computed");
        
        // Verify Signal State
        assertTrue(signalStateRepository.count() > 0, "Signal states should be detected");
        
        // Verify Market Pulse
        assertTrue(marketBreadthSnapshotRepository.count() > 0, "Market pulse snapshot should be created");
        
        // Verify Ingestion Run
        List<IngestionRun> runs = ingestionRunRepository.findAll();
        assertEquals(1, runs.size());
        assertEquals(IngestionRunStatus.SUCCESS, runs.get(0).getStatus());
    }

    @Test
    void shouldBeIdempotentOnRerun() {
        // Seed and run once
        Asset btc = assetRepository.save(new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true));
        Instant yesterday = Instant.now().atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant().minus(1, ChronoUnit.DAYS);
        CandleDto dto = new CandleDto(
            new BigDecimal("40000"), new BigDecimal("41000"), new BigDecimal("39000"), new BigDecimal("40500"),
            new BigDecimal("1000"), yesterday, yesterday.plus(1, ChronoUnit.DAYS).minusMillis(1)
        );
        when(binanceProvider.fetchDailyCandles(eq("BTCUSDT"), any(), any())).thenReturn(List.of(dto));

        marketPipelineService.runDaily(MarketProvider.BINANCE, Timeframe.D1, RunMode.INCREMENTAL);
        long candleCount = candleRepository.count();
        long runCount = ingestionRunRepository.count();

        // Run again
        marketPipelineService.runDaily(MarketProvider.BINANCE, Timeframe.D1, RunMode.INCREMENTAL);

        // Verify counts remain same (except ingestion_run)
        assertEquals(candleCount, candleRepository.count(), "Candle count should not change");
        assertEquals(runCount + 1, ingestionRunRepository.count(), "A new ingestion run should be created");
    }

    @Test
    void shouldMarkRunAsFailedOnException() {
        // Given: Ingestion fails
        assetRepository.save(new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true));
        when(binanceProvider.fetchDailyCandles(any(), any(), any())).thenThrow(new RuntimeException("Mocked failure"));

        // When
        marketPipelineService.runDaily(MarketProvider.BINANCE, Timeframe.D1, RunMode.INCREMENTAL);

        // Then
        List<IngestionRun> runs = ingestionRunRepository.findAll();
        assertEquals(1, runs.size());
        assertEquals(IngestionRunStatus.FAILED, runs.get(0).getStatus());
        assertNotNull(runs.get(0).getErrorSample());
        assertTrue(runs.get(0).getErrorSample().contains("Mocked failure"));
    }
}
