package walshe.projectcolumbo.supertrend.api;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.testtools.HttpClient;
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
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.AssetLiquidityDao;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.persistence.SchemaMigrator;
import walshe.projectcolumbo.supertrend.persistence.SignalStateDao;
import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.SignalEvent;
import walshe.projectcolumbo.supertrend.signal.SignalQueryService;
import walshe.projectcolumbo.supertrend.signal.SignalState;
import walshe.projectcolumbo.supertrend.signal.ScanService;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ScanHandlerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime NOW = OffsetDateTime.of(2024, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime CLOSE_TIME = OffsetDateTime.of(2024, 1, 14, 0, 0, 0, 0, ZoneOffset.UTC);

    static DataSource dataSource;
    static AssetDao assetDao;
    static CandleDao candleDao;
    static SignalStateDao signalStateDao;
    static SignalQueryService signalQueryService;

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
        signalStateDao = new SignalStateDao(dataSource);
        AssetLiquidityDao assetLiquidityDao = new AssetLiquidityDao(dataSource);
        signalQueryService = new SignalQueryService(assetDao, signalStateDao, candleDao, assetLiquidityDao);
    }

    private static Javalin newApp() {
        ScanService scanService = new ScanService(signalQueryService, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
        Javalin app = ApiServer.create();
        new ScanHandler(scanService).register(app);
        return app;
    }

    @BeforeEach
    void deactivateSeededAssets() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE asset SET active = false");
        }
    }

    // HttpClient.post(path, Object) always JSON-serializes the Object argument via Jackson - passing
    // an already-built okhttp3.RequestBody there gets bean-serialized as a POJO instead of sent as
    // the literal body (surfaces as a bogus "duplex" field in the request). Building the raw request
    // via request(path, Consumer<Request.Builder>) instead sends the JSON string exactly as written.
    private static Response postJson(HttpClient client, String path, String body) {
        return client.request(path, builder -> builder.post(RequestBody.create(body, MediaType.get("application/json"))));
    }

    @Test
    void singleConditionMatchesBullishAssets() {
        long assetId = seedAsset("SCH1USDT");
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));

        JavalinTest.test(newApp(), (server, client) -> {
            Response response = postJson(client, "/api/v1/scan", """
                    {"operator":"AND","conditions":[{"timeframe":"D1","state":"BULLISH"}]}
                    """);
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).contains("SCH1USDT");
        });
    }

    @Test
    void andAcrossTimeframesRequiresBothConditions() {
        long matches = seedAsset("SCH2AUSDT");
        long onlyD1 = seedAsset("SCH2BUSDT");
        signalStateDao.upsert(new SignalState(matches, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(matches, Timeframe.W1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(onlyD1, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));

        JavalinTest.test(newApp(), (server, client) -> {
            Response response = postJson(client, "/api/v1/scan", """
                    {"operator":"AND","conditions":[
                        {"timeframe":"D1","state":"BULLISH"},
                        {"timeframe":"W1","state":"BULLISH"}
                    ]}
                    """);
            String body = response.body().string();
            assertThat(body).contains("SCH2AUSDT").doesNotContain("SCH2BUSDT");
        });
    }

    @Test
    void limitTruncatesResults() {
        for (String symbol : List.of("SCH3AUSDT", "SCH3BUSDT", "SCH3CUSDT")) {
            long assetId = seedAsset(symbol);
            signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        }

        JavalinTest.test(newApp(), (server, client) -> {
            Response response = postJson(client, "/api/v1/scan", """
                    {"operator":"AND","conditions":[{"timeframe":"D1","state":"BULLISH"}],"limit":2}
                    """);
            String body = response.body().string();
            long matchCount = List.of("SCH3AUSDT", "SCH3BUSDT", "SCH3CUSDT").stream().filter(body::contains).count();
            assertThat(matchCount).isEqualTo(2);
        });
    }

    @Test
    void missingConditionsIsRejectedWith400() {
        JavalinTest.test(newApp(), (server, client) -> {
            Response response = postJson(client, "/api/v1/scan", """
                    {"operator":"AND","conditions":[]}
                    """);
            assertThat(response.code()).isEqualTo(400);
        });
    }

    @Test
    void nullConditionEntryIsRejectedWith400NotAServerError() {
        JavalinTest.test(newApp(), (server, client) -> {
            Response response = postJson(client, "/api/v1/scan", """
                    {"operator":"AND","conditions":[null]}
                    """);
            assertThat(response.code()).isEqualTo(400);
        });
    }

    @Test
    void malformedJsonBodyIsRejectedWith400() {
        JavalinTest.test(newApp(), (server, client) -> {
            Response response = postJson(client, "/api/v1/scan", "not json");
            assertThat(response.code()).isEqualTo(400);
        });
    }

    @Test
    void negativeLimitIsRejectedWith400() {
        JavalinTest.test(newApp(), (server, client) -> {
            Response response = postJson(client, "/api/v1/scan", """
                    {"operator":"AND","conditions":[{"timeframe":"D1","state":"BULLISH"}],"limit":-1}
                    """);
            assertThat(response.code()).isEqualTo(400);
        });
    }

    @Test
    void assetClassFilterRestrictsMatchesToThatClass() {
        long crypto = seedAsset("SCH4AUSDT", AssetClass.CRYPTO);
        long stock = seedAsset("SCH4BSTOCK", AssetClass.STOCK);
        signalStateDao.upsert(new SignalState(crypto, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(stock, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));

        JavalinTest.test(newApp(), (server, client) -> {
            Response response = postJson(client, "/api/v1/scan", """
                    {"operator":"AND","conditions":[{"timeframe":"D1","state":"BULLISH"}],"assetClass":"STOCK"}
                    """);
            String body = response.body().string();
            assertThat(body).contains("SCH4BSTOCK").doesNotContain("SCH4AUSDT");
        });
    }

    @Test
    void liquiditySortOrdersResultsByVolumeDescending() {
        long low = seedAsset("SCH6LOWUSDT");
        long high = seedAsset("SCH6HIGHUSDT");
        OffsetDateTime recentClose = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        seedCandle(low, recentClose, new BigDecimal("10"));
        seedCandle(high, recentClose, new BigDecimal("1000"));
        signalStateDao.upsert(new SignalState(low, Timeframe.D1, recentClose, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(high, Timeframe.D1, recentClose, TrendState.BULLISH, SignalEvent.NONE));

        JavalinTest.test(newApp(), (server, client) -> {
            Response response = postJson(client, "/api/v1/scan", """
                    {"operator":"AND","conditions":[{"timeframe":"D1","state":"BULLISH"}],"sort":"LIQUIDITY_DESC"}
                    """);
            String body = response.body().string();
            assertThat(response.code()).isEqualTo(200);
            assertThat(body.indexOf("SCH6HIGHUSDT")).isLessThan(body.indexOf("SCH6LOWUSDT"));
        });
    }

    @Test
    void omittingSortKeepsSymbolAscendingOrder() {
        long z = seedAsset("SCH7ZUSDT");
        long a = seedAsset("SCH7AUSDT");
        signalStateDao.upsert(new SignalState(z, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(a, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));

        JavalinTest.test(newApp(), (server, client) -> {
            Response response = postJson(client, "/api/v1/scan", """
                    {"operator":"AND","conditions":[{"timeframe":"D1","state":"BULLISH"}]}
                    """);
            String body = response.body().string();
            assertThat(body.indexOf("SCH7AUSDT")).isLessThan(body.indexOf("SCH7ZUSDT"));
        });
    }

    @Test
    void assetClassFilterWithNoMatchesReturnsEmptyResultsNotError() {
        long crypto = seedAsset("SCH5USDT", AssetClass.CRYPTO);
        signalStateDao.upsert(new SignalState(crypto, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));

        JavalinTest.test(newApp(), (server, client) -> {
            Response response = postJson(client, "/api/v1/scan", """
                    {"operator":"AND","conditions":[{"timeframe":"D1","state":"BULLISH"}],"assetClass":"ETF"}
                    """);
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).contains("\"results\":[]");
        });
    }

    private static long seedAsset(String symbol) {
        return seedAsset(symbol, AssetClass.CRYPTO);
    }

    private static long seedAsset(String symbol, AssetClass assetClass) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO asset (symbol, provider, active, asset_class) VALUES (?, ?::provider, true, ?::asset_class)")) {
            statement.setString(1, symbol);
            statement.setString(2, Provider.BINANCE.name());
            statement.setString(3, assetClass.name());
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

    private static void seedCandle(long assetId, OffsetDateTime closeTime, BigDecimal volume) {
        candleDao.upsert(assetId, new Candle(
                closeTime.minusDays(1), closeTime, Timeframe.D1,
                new BigDecimal("50.00"), new BigDecimal("50.00"), new BigDecimal("50.00"), new BigDecimal("50.00"), volume));
    }
}
