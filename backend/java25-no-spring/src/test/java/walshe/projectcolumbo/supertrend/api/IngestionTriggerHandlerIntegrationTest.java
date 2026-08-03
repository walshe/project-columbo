package walshe.projectcolumbo.supertrend.api;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import walshe.projectcolumbo.supertrend.indicator.IndicatorComputationService;
import walshe.projectcolumbo.supertrend.ingestion.CandleIngestionService;
import walshe.projectcolumbo.supertrend.ingestion.IngestionConfig;
import walshe.projectcolumbo.supertrend.ingestion.MarketDataProvider;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.persistence.IngestionRunDao;
import walshe.projectcolumbo.supertrend.persistence.SchemaMigrator;
import walshe.projectcolumbo.supertrend.persistence.SuperTrendIndicatorDao;
import walshe.projectcolumbo.supertrend.pipeline.IngestionRunStatus;
import walshe.projectcolumbo.supertrend.pipeline.PipelineOrchestrator;
import walshe.projectcolumbo.supertrend.rollup.CandleRollupService;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class IngestionTriggerHandlerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime BACKFILL_START = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime NOW = BACKFILL_START.plusDays(10);

    static DataSource dataSource;
    static AssetDao assetDao;
    static CandleDao candleDao;
    static IngestionRunDao ingestionRunDao;

    @BeforeAll
    static void migrate() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
        SchemaMigrator.migrate(dataSource);
        assetDao = new AssetDao(dataSource);
        candleDao = new CandleDao(dataSource);
        ingestionRunDao = new IngestionRunDao(dataSource);
    }

    @BeforeEach
    void resetState() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE asset SET active = false");
            // concurrentTriggerIsRejectedWith409 deliberately leaves a row stuck at RUNNING forever
            // (never completed) - without this, whichever test runs after it would incorrectly see
            // a run "already in progress" for the same provider+timeframe and get 409 instead of 202.
            statement.executeUpdate("DELETE FROM ingestion_run");
        }
    }

    private static Javalin newApp(MarketDataProvider provider) {
        Clock clock = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);
        IngestionConfig ingestionConfig = new IngestionConfig(BACKFILL_START);
        CandleIngestionService candleIngestionService = new CandleIngestionService(assetDao, candleDao, provider, ingestionConfig, clock);
        IndicatorComputationService indicatorComputationService =
                new IndicatorComputationService(assetDao, candleDao, new SuperTrendIndicatorDao(dataSource));
        CandleRollupService candleRollupService = new CandleRollupService(assetDao, candleDao, clock);
        PipelineOrchestrator orchestrator = new PipelineOrchestrator(
                assetDao, ingestionRunDao, candleIngestionService, indicatorComputationService, candleRollupService, clock);

        Javalin app = ApiServer.create();
        new IngestionTriggerHandler(orchestrator).register(app);
        return app;
    }

    private static RequestBody json(String body) {
        return RequestBody.create(body, MediaType.get("application/json"));
    }

    @Test
    void missingBodyDefaultsToBinanceD1AndReturns202WithARunId() throws Exception {
        JavalinTest.test(newApp((symbol, start, end) -> List.of()), (server, client) -> {
            Response response = client.post("/api/v1/internal/ingestion/run"); // no body at all
            assertThat(response.code()).isEqualTo(202);
            String body = response.body().string();
            assertThat(body).contains("\"status\":\"STARTED\"").contains("\"runId\"");

            long runId = extractRunId(body);
            IngestionRunStatus finalStatus = awaitCompletion(runId);
            assertThat(finalStatus).isIn(IngestionRunStatus.SUCCESS, IngestionRunStatus.FAILED, IngestionRunStatus.PARTIAL);
            assertThat(ingestionRunDao.findById(runId).orElseThrow().provider()).isEqualTo(Provider.BINANCE);
            assertThat(ingestionRunDao.findById(runId).orElseThrow().timeframe()).isEqualTo(Timeframe.D1);
        });
    }

    @Test
    void explicitProviderAndTimeframeAreHonored() {
        JavalinTest.test(newApp((symbol, start, end) -> List.of()), (server, client) -> {
            Response response = client.request("/api/v1/internal/ingestion/run", builder -> builder.post(json("""
                    {"provider":"BINANCE","timeframe":"W1"}
                    """)));
            assertThat(response.code()).isEqualTo(202);
        });
    }

    @Test
    void concurrentTriggerIsRejectedWith409() {
        ingestionRunDao.start(Provider.BINANCE, Timeframe.D1, 0, OffsetDateTime.now(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)));

        JavalinTest.test(newApp((symbol, start, end) -> List.of()), (server, client) -> {
            Response response = client.request("/api/v1/internal/ingestion/run", builder -> builder.post(json("{}")));
            assertThat(response.code()).isEqualTo(409);
        });
    }

    @Test
    void malformedBodyIsRejectedWith400() {
        JavalinTest.test(newApp((symbol, start, end) -> List.of()), (server, client) -> {
            Response response = client.request("/api/v1/internal/ingestion/run", builder -> builder.post(json("not json")));
            assertThat(response.code()).isEqualTo(400);
        });
    }

    private static long extractRunId(String jsonBody) {
        // Cheap extraction to avoid pulling in a JSON parser just for one field in a test.
        String marker = "\"runId\":";
        int start = jsonBody.indexOf(marker) + marker.length();
        int end = jsonBody.indexOf(',', start);
        if (end == -1) {
            end = jsonBody.indexOf('}', start);
        }
        return Long.parseLong(jsonBody.substring(start, end).trim());
    }

    private static IngestionRunStatus awaitCompletion(long runId) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            IngestionRunStatus status = ingestionRunDao.findById(runId).orElseThrow().status();
            if (status != IngestionRunStatus.RUNNING) {
                return status;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Run " + runId + " did not complete in time");
    }
}
