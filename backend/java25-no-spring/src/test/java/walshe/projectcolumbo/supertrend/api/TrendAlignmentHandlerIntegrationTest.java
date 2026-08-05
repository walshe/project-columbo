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
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.AssetLiquidityDao;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.persistence.IngestionRunDao;
import walshe.projectcolumbo.supertrend.persistence.SchemaMigrator;
import walshe.projectcolumbo.supertrend.persistence.SignalStateDao;
import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.SignalEvent;
import walshe.projectcolumbo.supertrend.signal.SignalQueryService;
import walshe.projectcolumbo.supertrend.signal.SignalState;
import walshe.projectcolumbo.supertrend.signal.TrendAlignmentService;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class TrendAlignmentHandlerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime CLOSE_TIME = OffsetDateTime.of(2024, 3, 14, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime NOW = CLOSE_TIME.plusDays(1).plusHours(1); // D1's expected latest close matches CLOSE_TIME at this NOW

    static DataSource dataSource;
    static AssetDao assetDao;
    static CandleDao candleDao;
    static SignalStateDao signalStateDao;
    static IngestionRunDao ingestionRunDao;
    static TrendAlignmentService trendAlignmentService;

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
        ingestionRunDao = new IngestionRunDao(dataSource);
        AssetLiquidityDao assetLiquidityDao = new AssetLiquidityDao(dataSource);
        SignalQueryService signalQueryService = new SignalQueryService(assetDao, signalStateDao, candleDao, assetLiquidityDao);
        trendAlignmentService = new TrendAlignmentService(signalQueryService, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
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
        new TrendAlignmentHandler(trendAlignmentService, freshnessService, clock).register(app);
        return app;
    }

    @Test
    void defaultFormatIsJsonWithBullishConfluenceAndFreshnessFields() {
        long assetId = seedAsset("TH1USDT");
        signalStateDao.upsert(new SignalState(assetId, Timeframe.W1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        seedCandle(assetId, CLOSE_TIME);

        JavalinTest.test(appWithFixedClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)), (server, client) -> {
            Response response = client.get("/api/v1/summary/trend-alignment");
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.header("Content-Type")).contains("application/json");
            String body = response.body().string();
            assertThat(body).contains("\"symbol\":\"TH1USDT\"").contains("\"bullishConfluence\"").contains("\"stale\":false")
                    .contains("\"maxRetestAgeDays\":7");
        });
    }

    @Test
    void markdownFormatRendersSections() {
        long assetId = seedAsset("TH2USDT");
        signalStateDao.upsert(new SignalState(assetId, Timeframe.W1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));

        JavalinTest.test(appWithFixedClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)), (server, client) -> {
            Response response = client.get("/api/v1/summary/trend-alignment?format=markdown&maxRetestAgeDays=10");
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.header("Content-Type")).contains("text/markdown");
            String body = response.body().string();
            assertThat(body).contains("# SuperTrend Trend Alignment").contains("TH2USDT").contains("Bearish Retest")
                    .contains("**Timeframes:** W1 + D1").contains("**Max Retest Age:** 10 day(s)");
        });
    }

    @Test
    void watchlistFormatOmitsEmptySections() {
        long assetId = seedAsset("TH3USDT");
        signalStateDao.upsert(new SignalState(assetId, Timeframe.W1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));

        JavalinTest.test(appWithFixedClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)), (server, client) -> {
            Response response = client.get("/api/v1/summary/trend-alignment?format=WATCHLIST");
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.header("Content-Type")).contains("text/plain");
            String body = response.body().string();
            assertThat(body).contains("### Bullish Confluence").contains("TH3USDT").doesNotContain("Bearish Confluence");
        });
    }

    @Test
    void invalidFormatIsRejectedWith400() {
        JavalinTest.test(appWithFixedClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)), (server, client) ->
                assertThat(client.get("/api/v1/summary/trend-alignment?format=xml").code()).isEqualTo(400));
    }

    @Test
    void nonNumericMaxRetestAgeDaysIsRejectedWith400() {
        JavalinTest.test(appWithFixedClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)), (server, client) ->
                assertThat(client.get("/api/v1/summary/trend-alignment?maxRetestAgeDays=notanumber").code()).isEqualTo(400));
    }

    @Test
    void requireFreshRejectsStaleD1DataWith503() throws Exception {
        // TrendAlignmentHandler always checks D1 specifically (never parameterizable, unlike
        // /signals), so unlike SignalsHandlerIntegrationTest this can't isolate itself onto W1
        // instead - it must clear D1 candles left behind by other tests in this class sharing the
        // same Testcontainers Postgres, or a fresher one would make findLatestCloseTimeAcrossAllAssets
        // (a genuinely global MAX() query) report up to date regardless of test order.
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM candle WHERE timeframe = 'D1'");
        }
        Clock farPastGrace = Clock.fixed(NOW.plusHours(6).toInstant(), ZoneOffset.UTC);

        JavalinTest.test(appWithFixedClock(farPastGrace), (server, client) -> {
            Response response = client.get("/api/v1/summary/trend-alignment?requireFresh=true");
            assertThat(response.code()).isEqualTo(503);
            assertThat(response.header("Retry-After")).isNotNull();
        });
    }

    @Test
    void assetClassFilterRestrictsConfluenceAndIsEchoedInMarkdown() {
        long crypto = seedAsset("TH4AUSDT", AssetClass.CRYPTO);
        long stock = seedAsset("TH4BSTOCK", AssetClass.STOCK);
        signalStateDao.upsert(new SignalState(crypto, Timeframe.W1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(crypto, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(stock, Timeframe.W1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(stock, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));

        JavalinTest.test(appWithFixedClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)), (server, client) -> {
            String jsonBody = client.get("/api/v1/summary/trend-alignment?assetClass=STOCK").body().string();
            assertThat(jsonBody).contains("TH4BSTOCK").doesNotContain("TH4AUSDT");

            String markdownBody = client.get("/api/v1/summary/trend-alignment?assetClass=STOCK&format=markdown").body().string();
            assertThat(markdownBody).contains("**Asset Class:** STOCK");
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

    private static void seedCandle(long assetId, OffsetDateTime closeTime) {
        candleDao.upsert(assetId, new walshe.projectcolumbo.supertrend.indicator.Candle(
                closeTime.minusDays(1), closeTime, Timeframe.D1,
                java.math.BigDecimal.valueOf(100), java.math.BigDecimal.valueOf(110),
                java.math.BigDecimal.valueOf(90), java.math.BigDecimal.valueOf(105), java.math.BigDecimal.valueOf(1000)));
    }
}
