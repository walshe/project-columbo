package walshe.projectcolumbo.ingestion;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.CandleRepository;
import walshe.projectcolumbo.persistence.repository.MarketBreadthSnapshotRepository;
import walshe.projectcolumbo.persistence.repository.EmaRepository;
import walshe.projectcolumbo.persistence.repository.MacdRepository;
import walshe.projectcolumbo.persistence.repository.RsiRepository;
import walshe.projectcolumbo.persistence.repository.SignalStateRepository;
import walshe.projectcolumbo.persistence.repository.SuperTrendRepository;
import walshe.projectcolumbo.persistence.repository.ThermometerRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import walshe.projectcolumbo.TestDatabaseCleaner;
import walshe.projectcolumbo.TestcontainersConfiguration;
import walshe.projectcolumbo.marketdata.CandleDto;
import walshe.projectcolumbo.marketdata.MarketDataProvider;


import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class MarketPipelineIntegrationTest {

    @Autowired
    private TestDatabaseCleaner testDatabaseCleaner;

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

    @Autowired
    private EmaRepository emaRepository;

    @Autowired
    private MacdRepository macdRepository;

    @Autowired
    private ThermometerRepository thermometerRepository;

    @MockitoBean(name = "binanceMarketDataProvider")
    private MarketDataProvider binanceProvider;

    @BeforeEach
    void setUp() {
        testDatabaseCleaner.cleanAll();

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

    @Test
    void shouldProduceW1OutputsAfterFullPipelineRun() {
        // Seed the BTC asset
        Asset btc = assetRepository.save(new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true));

        // Seed 16 complete Mon-Sun weeks of D1 candles (112 days) starting 2024-01-01 (a Monday UTC).
        // W1 RSI (period 14) needs at least 15 W1 candles, so 16 weeks ensures >= 15 complete weeks.
        // OHLC values oscillate to produce non-degenerate RSI/SuperTrend output.
        OffsetDateTime anchor = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        int totalDays = 16 * 7; // 112 days
        List<CandleDto> candles = new ArrayList<>(totalDays);
        for (int i = 0; i < totalDays; i++) {
            OffsetDateTime openTime = anchor.plusDays(i);
            OffsetDateTime closeTime = openTime.plusDays(1).minusNanos(1);
            // Oscillate close prices: alternating up/down pattern for meaningful indicators
            double base = 40000.0 + (i % 7) * 500.0;
            double close = (i % 2 == 0) ? base + 300 : base - 300;
            candles.add(new CandleDto(
                new BigDecimal(String.valueOf(base)),
                new BigDecimal(String.valueOf(base + 1000)),
                new BigDecimal(String.valueOf(base - 1000)),
                new BigDecimal(String.valueOf(close)),
                new BigDecimal("1000"),
                openTime.toInstant(),
                closeTime.toInstant()
            ));
        }
        when(binanceProvider.fetchDailyCandles(eq("BTCUSDT"), any(), any())).thenReturn(candles);

        // Run the full six-phase pipeline
        marketPipelineService.runDaily(MarketProvider.BINANCE, Timeframe.D1, RunMode.INCREMENTAL);

        // 1. D1 still works (PIPE-02): verify D1 candles, indicators, signals, breadth
        List<?> d1Candles = candleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(btc, Timeframe.D1);
        assertTrue(!d1Candles.isEmpty(), "D1 candles should be ingested");

        List<?> d1SuperTrend = superTrendRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(btc, Timeframe.D1);
        assertTrue(!d1SuperTrend.isEmpty(), "D1 SuperTrend indicators should be computed");

        List<?> d1Signals = signalStateRepository.findAll().stream()
                .filter(s -> s.getTimeframe() == Timeframe.D1)
                .toList();
        assertTrue(!d1Signals.isEmpty(), "D1 signal states should be detected");

        boolean d1BreadthExists = marketBreadthSnapshotRepository.findAll().stream()
                .anyMatch(s -> s.getTimeframe() == Timeframe.D1);
        assertTrue(d1BreadthExists, "D1 market breadth snapshot should exist");

        // 2. W1 candles exist (PIPE-01)
        List<?> w1Candles = candleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(btc, Timeframe.W1);
        assertTrue(w1Candles.size() >= 15, "At least 15 W1 candles should be produced; got: " + w1Candles.size());

        // 3. W1 indicators exist
        List<?> w1SuperTrend = superTrendRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(btc, Timeframe.W1);
        assertTrue(!w1SuperTrend.isEmpty(), "W1 SuperTrend indicators should be computed");

        List<?> w1Rsi = rsiRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(btc, Timeframe.W1);
        assertTrue(!w1Rsi.isEmpty(), "W1 RSI indicators should be computed");

        // 4. W1 signals exist
        boolean w1SignalsExist = signalStateRepository.findAll().stream()
                .anyMatch(s -> s.getTimeframe() == Timeframe.W1);
        assertTrue(w1SignalsExist, "W1 signal states should be detected");

        // 5. W1 breadth exists (PIPE-01)
        boolean w1BreadthExists = marketBreadthSnapshotRepository.findAll().stream()
                .anyMatch(s -> s.getTimeframe() == Timeframe.W1);
        assertTrue(w1BreadthExists, "W1 market breadth snapshot should exist");

        // 6. Run tracking reflects W1 (PIPE-03): exactly one IngestionRun, status SUCCESS
        List<IngestionRun> runs = ingestionRunRepository.findAll();
        assertEquals(1, runs.size(), "Exactly one IngestionRun should be recorded");
        assertEquals(IngestionRunStatus.SUCCESS, runs.get(0).getStatus(),
                "IngestionRun status should be SUCCESS after full six-phase run");
    }
}
