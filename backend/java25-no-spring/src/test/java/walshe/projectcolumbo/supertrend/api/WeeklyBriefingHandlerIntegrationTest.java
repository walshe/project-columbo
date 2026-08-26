package walshe.projectcolumbo.supertrend.api;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.indicator.IndicatorComputationService;
import walshe.projectcolumbo.supertrend.ingestion.CandleIngestionService;
import walshe.projectcolumbo.supertrend.ingestion.IngestionConfig;
import walshe.projectcolumbo.supertrend.ingestion.MarketDataProvider;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.AssetLiquidityDao;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.persistence.IngestionRunDao;
import walshe.projectcolumbo.supertrend.persistence.MarketBreadthSnapshotDao;
import walshe.projectcolumbo.supertrend.persistence.SchemaMigrator;
import walshe.projectcolumbo.supertrend.persistence.SignalStateDao;
import walshe.projectcolumbo.supertrend.persistence.SuperTrendIndicatorDao;
import walshe.projectcolumbo.supertrend.pipeline.PipelineOrchestrator;
import walshe.projectcolumbo.supertrend.pulse.MarketBreadthPulseService;
import walshe.projectcolumbo.supertrend.rollup.CandleRollupService;
import walshe.projectcolumbo.supertrend.shared.AssetVenue;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.ProvisionalTrendService;
import walshe.projectcolumbo.supertrend.signal.ScanService;
import walshe.projectcolumbo.supertrend.signal.SignalQueryService;
import walshe.projectcolumbo.supertrend.signal.SignalStateDetectionService;
import walshe.projectcolumbo.supertrend.signal.TrendAlignmentService;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for a real production bug: {@code WeeklyTrendBriefingHandler}/
 * {@code WeeklyPullbackBriefingHandler} built their {@code regimePulses} map with
 * {@code Collectors.toMap}, which throws {@code NullPointerException} the moment any one of
 * {@code BRIEFING_ASSET_CLASSES} has no active assets (so {@code marketBreadthSnapshotDao
 * .findLatest(...)} is empty, and {@code .orElse(null)} feeds a null value into the collector -
 * {@code Collectors.toMap} is merge()-based internally and rejects null values). This was latent
 * until {@code fix-pipeline-connection-pool-exhaustion} retired Binance's tokenized ETF assets
 * entirely, leaving zero active ETF-class assets in production and turning the latent bug into a
 * live 500 on every weekly briefing call. Seeds only a CRYPTO asset here (no STOCK/ETF at all) to
 * reproduce that exact shape without needing the full 97-asset production universe.
 */
@Testcontainers
class WeeklyBriefingHandlerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime BACKFILL_START = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime NOW = BACKFILL_START.plusDays(75);

    private static DataSource dataSource;
    private static AssetDao assetDao;
    private static CandleDao candleDao;

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

        // Only ever a CRYPTO asset is seeded in this test - no STOCK/ETF at all, matching
        // production reality post-V22 (tokenized STOCK/ETF fully retired) and reproducing the
        // exact condition that crashed both briefing endpoints: at least one of
        // BRIEFING_ASSET_CLASSES (ETF) has zero active assets.
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE asset SET active = false");
        }
        seedAsset("WBTCUSDT");
    }

    private static Javalin newApp() {
        Clock clock = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);
        IngestionConfig ingestionConfig = new IngestionConfig(BACKFILL_START);
        MarketDataProvider provider = (symbol, start, end) -> dailyCandles(70);
        CandleIngestionService candleIngestionService = new CandleIngestionService(
                assetDao, candleDao,
                Map.of(AssetVenue.SPOT, provider, AssetVenue.FUTURES, provider),
                ingestionConfig, clock);
        SignalStateDao signalStateDao = new SignalStateDao(dataSource);
        MarketBreadthSnapshotDao marketBreadthSnapshotDao = new MarketBreadthSnapshotDao(dataSource);
        IngestionRunDao ingestionRunDao = new IngestionRunDao(dataSource);
        AssetLiquidityDao assetLiquidityDao = new AssetLiquidityDao(dataSource);

        IndicatorComputationService indicatorComputationService =
                new IndicatorComputationService(assetDao, candleDao, new SuperTrendIndicatorDao(dataSource), dataSource);
        CandleRollupService candleRollupService = new CandleRollupService(assetDao, candleDao, clock);
        SignalStateDetectionService signalStateDetectionService =
                new SignalStateDetectionService(assetDao, candleDao, signalStateDao, dataSource);
        MarketBreadthPulseService marketBreadthPulseService =
                new MarketBreadthPulseService(assetDao, signalStateDao, marketBreadthSnapshotDao);
        PipelineOrchestrator pipelineOrchestrator = new PipelineOrchestrator(
                assetDao, ingestionRunDao, candleIngestionService, indicatorComputationService,
                candleRollupService, signalStateDetectionService, marketBreadthPulseService, clock);

        SignalQueryService signalQueryService = new SignalQueryService(assetDao, signalStateDao, candleDao, assetLiquidityDao);
        TrendAlignmentService trendAlignmentService = new TrendAlignmentService(signalQueryService, clock);
        ScanService scanService = new ScanService(signalQueryService, clock);
        ProvisionalTrendService provisionalTrendService = new ProvisionalTrendService(assetDao, candleDao, clock, dataSource);

        Javalin app = ApiServer.create();
        new WeeklyTrendBriefingHandler(pipelineOrchestrator, marketBreadthSnapshotDao, signalQueryService, trendAlignmentService, scanService, provisionalTrendService, clock).register(app);
        new WeeklyPullbackBriefingHandler(pipelineOrchestrator, marketBreadthSnapshotDao, signalQueryService, trendAlignmentService, scanService, provisionalTrendService, clock).register(app);
        return app;
    }

    @Test
    void weeklyTrendBriefingSucceedsWithNoActiveEtfAssets() {
        JavalinTest.test(newApp(), (server, client) -> {
            Response response = client.post("/api/v1/weekly-trend-briefing");
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).isNotBlank();
        });
    }

    @Test
    void weeklyPullbackBriefingSucceedsWithNoActiveEtfAssets() {
        JavalinTest.test(newApp(), (server, client) -> {
            Response response = client.post("/api/v1/weekly-pullback-briefing");
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).isNotBlank();
        });
    }

    private static void seedAsset(String symbol) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO asset (symbol, provider, active) VALUES (?, ?::provider, true)")) {
            statement.setString(1, symbol);
            statement.setString(2, Provider.BINANCE.name());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
}
