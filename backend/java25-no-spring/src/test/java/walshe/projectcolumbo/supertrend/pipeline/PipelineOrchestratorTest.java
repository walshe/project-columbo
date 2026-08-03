package walshe.projectcolumbo.supertrend.pipeline;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.indicator.IndicatorComputationService;
import walshe.projectcolumbo.supertrend.ingestion.CandleIngestionService;
import walshe.projectcolumbo.supertrend.ingestion.IngestionConfig;
import walshe.projectcolumbo.supertrend.ingestion.InvalidSymbolException;
import walshe.projectcolumbo.supertrend.ingestion.MarketDataProvider;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.persistence.IngestionRunDao;
import walshe.projectcolumbo.supertrend.persistence.MarketBreadthSnapshotDao;
import walshe.projectcolumbo.supertrend.persistence.SchemaMigrator;
import walshe.projectcolumbo.supertrend.persistence.SignalStateDao;
import walshe.projectcolumbo.supertrend.persistence.SuperTrendIndicatorDao;
import walshe.projectcolumbo.supertrend.pulse.MarketBreadthPulseService;
import walshe.projectcolumbo.supertrend.rollup.CandleRollupService;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.SignalStateDetectionService;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The 60 migration-seeded assets are deactivated in @BeforeAll so runDaily()'s ingestion phase
// (200ms polite delay per asset) only touches the assets each test actually cares about.
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PipelineOrchestratorTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime BACKFILL_START = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC); // a Monday
    private static final OffsetDateTime NOW = BACKFILL_START.plusDays(75);

    static DataSource dataSource;
    static AssetDao assetDao;
    static CandleDao candleDao;
    static SuperTrendIndicatorDao superTrendIndicatorDao;
    static IngestionRunDao ingestionRunDao;
    static SignalStateDao signalStateDao;
    static MarketBreadthSnapshotDao marketBreadthSnapshotDao;
    static IngestionConfig ingestionConfig;
    static Clock clock;

    @BeforeAll
    static void setUp() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
        SchemaMigrator.migrate(dataSource);
        assetDao = new AssetDao(dataSource);
        candleDao = new CandleDao(dataSource);
        superTrendIndicatorDao = new SuperTrendIndicatorDao(dataSource);
        ingestionRunDao = new IngestionRunDao(dataSource);
        signalStateDao = new SignalStateDao(dataSource);
        marketBreadthSnapshotDao = new MarketBreadthSnapshotDao(dataSource);
        ingestionConfig = new IngestionConfig(BACKFILL_START);
        clock = Clock.fixed(Instant.from(NOW), ZoneOffset.UTC);

        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE asset SET active = false");
        }
    }

    @Test
    @Order(1)
    void successfulRunCompletesTheFullPhaseChainAndRecordsSuccessStatus() {
        long assetId = seedAsset("PIPE1USDT");
        FakeMarketDataProvider provider = new FakeMarketDataProvider();
        provider.onFetch("PIPE1USDT", () -> dailyCandles(70)); // 10 complete weeks - enough for both D1 and W1 ATR(10) warm-up

        PipelineRunResult result = orchestrator(provider).runDaily(Provider.BINANCE, Timeframe.D1);

        assertThat(result.status()).isEqualTo(IngestionRunStatus.SUCCESS);
        assertThat(ingestionRunDao.findById(result.runId())).isPresent();
        assertThat(ingestionRunDao.findById(result.runId()).get().status()).isEqualTo(IngestionRunStatus.SUCCESS);

        assertThat(candleDao.findByAssetAndTimeframe(assetId, Timeframe.D1)).hasSize(70);
        assertThat(superTrendIndicatorDao.findLatestCloseTime(assetId, Timeframe.D1)).isPresent();

        assertThat(candleDao.findByAssetAndTimeframe(assetId, Timeframe.W1)).hasSize(10);
        assertThat(superTrendIndicatorDao.findLatestCloseTime(assetId, Timeframe.W1)).isPresent();
    }

    @Test
    @Order(2)
    void rejectsANewRunWhileOneIsAlreadyRunning() {
        ingestionRunDao.start(Provider.BINANCE, Timeframe.W1, 0, OffsetDateTime.now(clock));

        assertThatThrownBy(() -> orchestrator(new FakeMarketDataProvider()).runDaily(Provider.BINANCE, Timeframe.W1))
                .isInstanceOf(IngestionAlreadyRunningException.class);
    }

    @Test
    @Order(3)
    void mixedAssetOutcomesProducePartialStatus() {
        seedAsset("PIPE3AUSDT");
        seedAsset("PIPE3BUSDT");
        FakeMarketDataProvider provider = new FakeMarketDataProvider();
        provider.onFetch("PIPE3AUSDT", () -> dailyCandles(3));
        provider.onFetch("PIPE3BUSDT", () -> {
            throw new InvalidSymbolException("PIPE3BUSDT");
        });

        PipelineRunResult result = orchestrator(provider).runDaily(Provider.BINANCE, Timeframe.D1);

        assertThat(result.status()).isEqualTo(IngestionRunStatus.PARTIAL);
    }

    @Test
    @Order(4)
    void allAssetsFailingProducesFailedStatus() {
        // Deactivate every asset seeded by earlier tests (PIPE1/PIPE3A/PIPE3B) - none of them
        // have a registered behavior in this test's fake provider, which would return an empty
        // list (not an error) and break the "all active assets failed" premise.
        deactivateAsset("PIPE1USDT");
        deactivateAsset("PIPE3AUSDT");
        deactivateAsset("PIPE3BUSDT");
        seedAsset("PIPE4USDT");
        FakeMarketDataProvider provider = new FakeMarketDataProvider();
        provider.onFetch("PIPE4USDT", () -> {
            throw new RuntimeException("provider unavailable");
        });

        PipelineRunResult result = orchestrator(provider).runDaily(Provider.BINANCE, Timeframe.D1);

        assertThat(result.status()).isEqualTo(IngestionRunStatus.FAILED);
    }

    @Test
    @Order(5)
    void triggerAsyncReturnsImmediatelyAndCompletesInTheBackground() throws InterruptedException {
        long assetId = seedAsset("PIPE5USDT");
        FakeMarketDataProvider provider = new FakeMarketDataProvider();
        provider.onFetch("PIPE5USDT", () -> dailyCandles(70));

        long runId = orchestrator(provider).triggerAsync(Provider.BINANCE, Timeframe.D1);

        IngestionRun run = awaitCompletion(runId);
        assertThat(run.status()).isEqualTo(IngestionRunStatus.SUCCESS);
        assertThat(candleDao.findByAssetAndTimeframe(assetId, Timeframe.D1)).hasSize(70);
    }

    @Test
    @Order(6)
    void triggerAsyncRejectsANewRunWhileOneIsAlreadyRunning() {
        // No fresh RUNNING row needed here - @Order(2) already left one stuck at RUNNING forever
        // for (BINANCE, W1) (runDaily was never called to complete it), and the unique partial
        // index on (provider, timeframe) WHERE status = 'RUNNING' means inserting a second one
        // for the same pair would itself throw, rather than exercising the isRunning() fast path.
        assertThatThrownBy(() -> orchestrator(new FakeMarketDataProvider()).triggerAsync(Provider.BINANCE, Timeframe.W1))
                .isInstanceOf(IngestionAlreadyRunningException.class);
    }

    private static IngestionRun awaitCompletion(long runId) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            IngestionRun run = ingestionRunDao.findById(runId).orElseThrow();
            if (run.status() != IngestionRunStatus.RUNNING) {
                return run;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Run " + runId + " did not complete in time");
    }

    private static PipelineOrchestrator orchestrator(MarketDataProvider provider) {
        CandleIngestionService candleIngestionService =
                new CandleIngestionService(assetDao, candleDao, provider, ingestionConfig, clock);
        IndicatorComputationService indicatorComputationService =
                new IndicatorComputationService(assetDao, candleDao, superTrendIndicatorDao);
        CandleRollupService candleRollupService = new CandleRollupService(assetDao, candleDao, clock);
        SignalStateDetectionService signalStateDetectionService = new SignalStateDetectionService(assetDao, candleDao, signalStateDao);
        MarketBreadthPulseService marketBreadthPulseService = new MarketBreadthPulseService(assetDao, signalStateDao, marketBreadthSnapshotDao);
        return new PipelineOrchestrator(assetDao, ingestionRunDao, candleIngestionService, indicatorComputationService,
                candleRollupService, signalStateDetectionService, marketBreadthPulseService, clock);
    }

    private static long seedAsset(String symbol) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO asset (symbol, provider, active) VALUES (?, ?::provider, true)")) {
            statement.setString(1, symbol);
            statement.setString(2, Provider.BINANCE.name());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return assetDao.findAllActive().stream()
                .filter(a -> a.symbol().equals(symbol))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private static void deactivateAsset(String symbol) {
        assetDao.findAllActive().stream()
                .filter(a -> a.symbol().equals(symbol))
                .findFirst()
                .ifPresent(a -> assetDao.deactivate(a.id()));
    }

    private static List<Candle> dailyCandles(int dayCount) {
        return java.util.stream.IntStream.range(0, dayCount).mapToObj(i -> {
            OffsetDateTime closeTime = BACKFILL_START.plusDays(i + 1);
            return new Candle(
                    closeTime.minusDays(1),
                    closeTime,
                    Timeframe.D1,
                    BigDecimal.valueOf(100 + i),
                    BigDecimal.valueOf(110 + i),
                    BigDecimal.valueOf(90 + i),
                    BigDecimal.valueOf(105 + i),
                    BigDecimal.valueOf(1000)
            );
        }).toList();
    }

    private static final class FakeMarketDataProvider implements MarketDataProvider {
        private final Map<String, Supplier<List<Candle>>> behaviors = new HashMap<>();

        void onFetch(String symbol, Supplier<List<Candle>> behavior) {
            behaviors.put(symbol, behavior);
        }

        @Override
        public List<Candle> fetchDailyCandles(String symbol, long startTimeMs, long endTimeMs) {
            Supplier<List<Candle>> behavior = behaviors.get(symbol);
            return behavior == null ? List.of() : behavior.get();
        }
    }
}
