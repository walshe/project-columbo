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
import walshe.projectcolumbo.supertrend.freshness.FreshnessService;
import walshe.projectcolumbo.supertrend.indicator.IndicatorComputationService;
import walshe.projectcolumbo.supertrend.ingestion.BinanceMarketDataProvider;
import walshe.projectcolumbo.supertrend.ingestion.CandleIngestionService;
import walshe.projectcolumbo.supertrend.ingestion.IngestionConfig;
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
import walshe.projectcolumbo.supertrend.signal.ScanService;
import walshe.projectcolumbo.supertrend.signal.SignalQueryService;
import walshe.projectcolumbo.supertrend.signal.SignalStateDetectionService;
import walshe.projectcolumbo.supertrend.signal.TrendAlignmentService;

import javax.sql.DataSource;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Registers every real handler (mimicking {@code Main}'s composition root, not the throwaway
 * routes {@link ApiServerIntegrationTest} uses) and confirms the generated OpenAPI spec actually
 * documents them. A prior version of this codebase had zero {@code @OpenApi}-annotated real
 * handler methods - the plugin infrastructure and its own throwaway-route tests all passed, but
 * the generated spec had no {@code paths} and Swagger UI's {@code urls} list was empty ("No API
 * definition provided"), because {@code OpenApiLoader.loadVersions()} has nothing to discover
 * without at least one annotated route anywhere in the compiled sources.
 */
@Testcontainers
class OpenApiDocumentationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;

    @BeforeAll
    static void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
        SchemaMigrator.migrate(dataSource);
    }

    private static Javalin newApp() {
        AssetDao assetDao = new AssetDao(dataSource);
        CandleDao candleDao = new CandleDao(dataSource);
        SignalStateDao signalStateDao = new SignalStateDao(dataSource);
        MarketBreadthSnapshotDao marketBreadthSnapshotDao = new MarketBreadthSnapshotDao(dataSource);
        IngestionRunDao ingestionRunDao = new IngestionRunDao(dataSource);
        AssetLiquidityDao assetLiquidityDao = new AssetLiquidityDao(dataSource);
        Clock clock = Clock.systemUTC();

        FreshnessService freshnessService = new FreshnessService(candleDao, ingestionRunDao, clock);
        SignalQueryService signalQueryService = new SignalQueryService(assetDao, signalStateDao, candleDao, assetLiquidityDao);
        TrendAlignmentService trendAlignmentService = new TrendAlignmentService(signalQueryService, clock);
        ScanService scanService = new ScanService(signalQueryService, clock);
        SignalStateDetectionService signalStateDetectionService = new SignalStateDetectionService(assetDao, candleDao, signalStateDao, dataSource);
        MarketBreadthPulseService marketBreadthPulseService = new MarketBreadthPulseService(assetDao, signalStateDao, marketBreadthSnapshotDao);
        // Real (but never-exercised) candleIngestionService/indicatorComputationService: this test
        // only exercises route registration and spec generation, never IngestionTriggerHandler's
        // actual triggerAsync() call path - but the constructor now requires non-null dependencies,
        // so these need to be real instances rather than null, even though nothing here calls them.
        HttpClient httpClient = HttpClient.newHttpClient();
        CandleIngestionService candleIngestionService = new CandleIngestionService(
                assetDao, candleDao,
                Map.of(
                        AssetVenue.SPOT, new BinanceMarketDataProvider(httpClient, AssetVenue.SPOT),
                        AssetVenue.FUTURES, new BinanceMarketDataProvider(httpClient, AssetVenue.FUTURES)),
                new IngestionConfig(null), clock);
        IndicatorComputationService indicatorComputationService =
                new IndicatorComputationService(assetDao, candleDao, new SuperTrendIndicatorDao(dataSource), dataSource);
        PipelineOrchestrator pipelineOrchestrator = new PipelineOrchestrator(
                assetDao, ingestionRunDao, candleIngestionService, indicatorComputationService,
                new CandleRollupService(assetDao, candleDao, clock), signalStateDetectionService, marketBreadthPulseService, clock);

        Javalin app = ApiServer.create();
        new SignalsHandler(signalQueryService, freshnessService).register(app);
        new TrendAlignmentHandler(trendAlignmentService, freshnessService, clock).register(app);
        new SummaryHandler(signalQueryService, marketBreadthSnapshotDao, freshnessService, clock).register(app);
        new ScanHandler(scanService).register(app);
        new CandleCoverageHandler(candleDao, freshnessService).register(app);
        new IngestionTriggerHandler(pipelineOrchestrator).register(app);
        return app;
    }

    @Test
    void generatedSpecDocumentsEveryRealEndpoint() {
        JavalinTest.test(newApp(), (server, client) -> {
            Response response = client.get("/openapi");
            assertThat(response.code()).isEqualTo(200);
            String body = response.body().string();
            assertThat(body)
                    .contains("\"/api/v1/signals\"")
                    .contains("\"/api/v1/assets/by-state\"")
                    .contains("\"/api/v1/summary\"")
                    .contains("\"/api/v1/summary/trend-alignment\"")
                    .contains("\"/api/v1/scan\"")
                    .contains("\"/api/v1/candles/coverage\"")
                    .contains("\"/api/v1/internal/ingestion/run\"");
        });
    }

    @Test
    void swaggerUiHasAtLeastOneDocumentationUrlToFetch() {
        JavalinTest.test(newApp(), (server, client) -> {
            Response response = client.get("/swagger");
            String body = response.body().string();
            // The exact bug this test guards against: an empty urls: [] array (no per-entry "url:"
            // key anywhere in the page) means Swagger UI has nothing to fetch and fails with
            // "No API definition provided" in a real browser - checking for at least one "url:"
            // entry is sufficient and doesn't depend on the plugin's exact HTML whitespace/formatting.
            assertThat(body).contains("urls:").contains("url:");
        });
    }
}
