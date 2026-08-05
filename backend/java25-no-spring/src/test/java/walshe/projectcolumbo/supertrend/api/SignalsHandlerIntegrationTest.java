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
import walshe.projectcolumbo.supertrend.signal.TrendState;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class SignalsHandlerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    // "Today" 01:00 UTC, with D1's expected latest close (yesterday's UTC midnight) matching CLOSE_TIME below.
    private static final OffsetDateTime CLOSE_TIME = OffsetDateTime.of(2024, 3, 14, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime NOW = CLOSE_TIME.plusDays(1).plusHours(1);

    static DataSource dataSource;
    static AssetDao assetDao;
    static CandleDao candleDao;
    static SignalStateDao signalStateDao;
    static AssetLiquidityDao assetLiquidityDao;
    static IngestionRunDao ingestionRunDao;
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
        assetLiquidityDao = new AssetLiquidityDao(dataSource);
        ingestionRunDao = new IngestionRunDao(dataSource);
        signalQueryService = new SignalQueryService(assetDao, signalStateDao, candleDao, assetLiquidityDao);
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
        new SignalsHandler(signalQueryService, freshnessService).register(app);
        return app;
    }

    @Test
    void signalsEndpointReturnsEnrichedSignalsAndFreshnessFields() {
        long assetId = seedAsset("SH1USDT");
        seedCandle(assetId, CLOSE_TIME, new BigDecimal("110"));
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        Clock clock = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);

        JavalinTest.test(appWithFixedClock(clock), (server, client) -> {
            Response response = client.get("/api/v1/signals?timeframe=D1");
            assertThat(response.code()).isEqualTo(200);
            String body = response.body().string();
            assertThat(body).contains("\"symbol\":\"SH1USDT\"").contains("\"trendState\":\"BULLISH\"").contains("\"stale\":false");
        });
    }

    @Test
    void signalsEndpointFiltersByStateCaseInsensitively() {
        long bullish = seedAsset("SH2AUSDT");
        long bearish = seedAsset("SH2BUSDT");
        signalStateDao.upsert(new SignalState(bullish, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(bearish, Timeframe.D1, CLOSE_TIME, TrendState.BEARISH, SignalEvent.NONE));
        Clock clock = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);

        JavalinTest.test(appWithFixedClock(clock), (server, client) -> {
            Response response = client.get("/api/v1/signals?timeframe=d1&state=bearish");
            String body = response.body().string();
            assertThat(body).contains("SH2BUSDT").doesNotContain("SH2AUSDT");
        });
    }

    @Test
    void signalsEndpointSortsBySortQueryParam() {
        long earlyFlip = seedAsset("SH3AUSDT");
        long lateFlip = seedAsset("SH3BUSDT");
        signalStateDao.upsert(new SignalState(earlyFlip, Timeframe.D1, CLOSE_TIME.minusDays(5), TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL));
        signalStateDao.upsert(new SignalState(lateFlip, Timeframe.D1, CLOSE_TIME.minusDays(1), TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL));
        Clock clock = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);

        JavalinTest.test(appWithFixedClock(clock), (server, client) -> {
            Response response = client.get("/api/v1/signals?timeframe=D1&sort=last_flip_desc");
            String body = response.body().string();
            assertThat(body.indexOf("SH3BUSDT")).isLessThan(body.indexOf("SH3AUSDT"));
        });
    }

    @Test
    void signalsEndpointRejectsMissingTimeframeWith400() {
        JavalinTest.test(appWithFixedClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)), (server, client) ->
                assertThat(client.get("/api/v1/signals").code()).isEqualTo(400));
    }

    // These three tests use W1, not D1: findLatestCloseTimeAcrossAllAssets is a genuinely global
    // MAX() over the shared Testcontainers Postgres instance (no per-test isolation), so a fresh
    // D1 candle seeded by another test in this class would make an "expect stale" assertion here
    // order-dependent. No other test in this class seeds W1 data, so these three stay mutually safe.

    @Test
    void signalsEndpointRejectsStaleDataWith503WhenRequireFreshIsTrue() {
        long assetId = seedAsset("SH4USDT");
        seedCandle(assetId, CLOSE_TIME.minusDays(10), Timeframe.W1, new BigDecimal("100")); // way behind, no recent ingestion
        signalStateDao.upsert(new SignalState(assetId, Timeframe.W1, CLOSE_TIME.minusDays(10), TrendState.BULLISH, SignalEvent.NONE));
        Clock farPastGrace = Clock.fixed(NOW.plusHours(6).toInstant(), ZoneOffset.UTC);

        JavalinTest.test(appWithFixedClock(farPastGrace), (server, client) -> {
            Response response = client.get("/api/v1/signals?timeframe=W1&requireFresh=true");
            assertThat(response.code()).isEqualTo(503);
            assertThat(response.header("Retry-After")).isNotNull();
        });
    }

    @Test
    void signalsEndpointDoesNotRejectStaleDataWhenRequireFreshIsAbsent() {
        long assetId = seedAsset("SH5USDT");
        seedCandle(assetId, CLOSE_TIME.minusDays(10), Timeframe.W1, new BigDecimal("100"));
        signalStateDao.upsert(new SignalState(assetId, Timeframe.W1, CLOSE_TIME.minusDays(10), TrendState.BULLISH, SignalEvent.NONE));
        Clock farPastGrace = Clock.fixed(NOW.plusHours(6).toInstant(), ZoneOffset.UTC);

        JavalinTest.test(appWithFixedClock(farPastGrace), (server, client) -> {
            Response response = client.get("/api/v1/signals?timeframe=W1");
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).contains("\"stale\":true");
        });
    }

    @Test
    void assetsByStateRequiresStateParamAndAppliesNoFreshnessGating() {
        long assetId = seedAsset("SH6USDT");
        seedCandle(assetId, CLOSE_TIME.minusDays(30), Timeframe.W1, new BigDecimal("100")); // very stale, but by-state doesn't gate on freshness
        signalStateDao.upsert(new SignalState(assetId, Timeframe.W1, CLOSE_TIME.minusDays(30), TrendState.BULLISH, SignalEvent.NONE));
        Clock clock = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);

        JavalinTest.test(appWithFixedClock(clock), (server, client) -> {
            assertThat(client.get("/api/v1/assets/by-state?timeframe=W1").code()).isEqualTo(400);

            Response response = client.get("/api/v1/assets/by-state?timeframe=W1&state=BULLISH");
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).contains("SH6USDT");
        });
    }

    @Test
    void signalsEndpointFiltersByAssetClass() {
        long crypto = seedAsset("SH7AUSDT", AssetClass.CRYPTO);
        long stock = seedAsset("SH7BSTOCK", AssetClass.STOCK);
        signalStateDao.upsert(new SignalState(crypto, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(stock, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        Clock clock = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);

        JavalinTest.test(appWithFixedClock(clock), (server, client) -> {
            Response response = client.get("/api/v1/signals?timeframe=D1&assetClass=STOCK");
            String body = response.body().string();
            assertThat(body).contains("SH7BSTOCK").doesNotContain("SH7AUSDT");
        });
    }

    @Test
    void signalsEndpointFilteredToAClassWithNoMatchesReturnsEmptyNotError() {
        long crypto = seedAsset("SH8USDT", AssetClass.CRYPTO);
        signalStateDao.upsert(new SignalState(crypto, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        Clock clock = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);

        JavalinTest.test(appWithFixedClock(clock), (server, client) -> {
            Response response = client.get("/api/v1/signals?timeframe=D1&assetClass=ETF");
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).contains("\"signals\":[]");
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

    private static void seedCandle(long assetId, OffsetDateTime closeTime, BigDecimal close) {
        seedCandle(assetId, closeTime, Timeframe.D1, close);
    }

    private static void seedCandle(long assetId, OffsetDateTime closeTime, Timeframe timeframe, BigDecimal close) {
        candleDao.upsert(assetId, new Candle(
                closeTime.minusDays(1), closeTime, timeframe,
                close, close, close, close, BigDecimal.valueOf(1000)));
    }
}
