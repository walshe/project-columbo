package walshe.projectcolumbo.supertrend.api;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import walshe.projectcolumbo.supertrend.freshness.FreshnessService;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.persistence.IngestionRunDao;
import walshe.projectcolumbo.supertrend.persistence.SchemaMigrator;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class CandleCoverageHandlerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime EARLIEST = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime CLOSE_TIME = OffsetDateTime.of(2024, 3, 14, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime NOW = CLOSE_TIME.plusDays(1).plusHours(1); // D1's expected latest close matches CLOSE_TIME at this NOW

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
    void deactivateSeededAssets() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE asset SET active = false");
        }
    }

    private static Javalin appWithFixedClock(Clock clock) {
        Javalin app = ApiServer.create();
        FreshnessService freshnessService = new FreshnessService(candleDao, ingestionRunDao, clock);
        new CandleCoverageHandler(candleDao, freshnessService).register(app);
        return app;
    }

    @Test
    void reportsCoveragePerTimeframeFromRealCandleData() throws Exception {
        deleteAllCandles(); // isolate from other tests in this class sharing one Testcontainers Postgres
        long assetA = seedAsset("CC1AUSDT");
        long assetB = seedAsset("CC1BUSDT");
        seedCandle(assetA, EARLIEST);
        seedCandle(assetA, CLOSE_TIME);
        seedCandle(assetB, CLOSE_TIME);

        JavalinTest.test(appWithFixedClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)), (server, client) -> {
            Response response = client.get("/api/v1/candles/coverage");
            assertThat(response.code()).isEqualTo(200);
            String body = response.body().string();
            assertThat(body).contains("\"D1\"").contains("\"W1\"")
                    .contains("\"assetCount\":2").contains("\"upToDate\":true");
        });
    }

    @Test
    void reportsNullEarliestAndLatestWhenNoCandlesExistForATimeframe() throws Exception {
        deleteAllCandles();

        JavalinTest.test(appWithFixedClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)), (server, client) -> {
            Response response = client.get("/api/v1/candles/coverage");
            String body = response.body().string();
            assertThat(body).contains("\"earliest\":null").contains("\"latest\":null").contains("\"assetCount\":0");
        });
    }

    private static void deleteAllCandles() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM candle");
        }
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

    private static void seedCandle(long assetId, OffsetDateTime closeTime) {
        candleDao.upsert(assetId, new Candle(
                closeTime.minusDays(1), closeTime, Timeframe.D1,
                BigDecimal.valueOf(100), BigDecimal.valueOf(110), BigDecimal.valueOf(90), BigDecimal.valueOf(105), BigDecimal.valueOf(1000)));
    }
}
