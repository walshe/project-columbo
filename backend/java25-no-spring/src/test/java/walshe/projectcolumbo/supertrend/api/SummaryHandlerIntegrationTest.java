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
import walshe.projectcolumbo.supertrend.persistence.MarketBreadthSnapshotDao;
import walshe.projectcolumbo.supertrend.persistence.SchemaMigrator;
import walshe.projectcolumbo.supertrend.persistence.SignalStateDao;
import walshe.projectcolumbo.supertrend.pulse.MarketBreadthSnapshot;
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
class SummaryHandlerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime CLOSE_TIME = OffsetDateTime.of(2024, 3, 14, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime NOW = CLOSE_TIME.plusDays(1).plusHours(1); // D1's expected latest close matches CLOSE_TIME at this NOW

    static DataSource dataSource;
    static AssetDao assetDao;
    static CandleDao candleDao;
    static SignalStateDao signalStateDao;
    static MarketBreadthSnapshotDao marketBreadthSnapshotDao;
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
        marketBreadthSnapshotDao = new MarketBreadthSnapshotDao(dataSource);
        ingestionRunDao = new IngestionRunDao(dataSource);
        AssetLiquidityDao assetLiquidityDao = new AssetLiquidityDao(dataSource);
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
        new SummaryHandler(signalQueryService, marketBreadthSnapshotDao, freshnessService, clock).register(app);
        return app;
    }

    @Test
    void defaultFormatIsJsonWithSignalsPulseAndFreshnessFields() {
        long assetId = seedAsset("SM1USDT");
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        seedCandle(assetId, CLOSE_TIME);
        marketBreadthSnapshotDao.upsert(new MarketBreadthSnapshot(Timeframe.D1, CLOSE_TIME, null, 1, 0, 0, 1, new BigDecimal("1.0000")));

        JavalinTest.test(appWithFixedClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)), (server, client) -> {
            Response response = client.get("/api/v1/summary?timeframe=D1");
            assertThat(response.code()).isEqualTo(200);
            String body = response.body().string();
            assertThat(body).contains("\"symbol\":\"SM1USDT\"").contains("\"bullishCount\":1").contains("\"stale\":false")
                    .contains("\"timeframe\":\"D1\"")
                    .doesNotContain("rsi").doesNotContain("Rsi").doesNotContain("RSI");
        });
    }

    @Test
    void pulseIsNullWhenNoSnapshotExistsYet() throws Exception {
        deleteD1MarketBreadthSnapshots(); // other tests in this class upsert one; market_breadth_snapshot is a single row per (timeframe, close_time), shared across the whole class's Testcontainers Postgres, not per-asset
        seedAsset("SM2USDT");

        JavalinTest.test(appWithFixedClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)), (server, client) -> {
            Response response = client.get("/api/v1/summary?timeframe=D1");
            assertThat(response.body().string()).contains("\"pulse\":null");
        });
    }

    @Test
    void markdownFormatOmitsPulseSectionWhenNoSnapshotExists() throws Exception {
        deleteD1MarketBreadthSnapshots();
        long assetId = seedAsset("SM3USDT");
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));

        JavalinTest.test(appWithFixedClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)), (server, client) -> {
            Response response = client.get("/api/v1/summary?timeframe=D1&format=markdown");
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.header("Content-Type")).contains("text/markdown");
            String body = response.body().string();
            assertThat(body).contains("# Market Summary Report").contains("**Timeframe:** D1").doesNotContain("## Market Pulse");
        });
    }

    @Test
    void markdownFormatIncludesPulseSectionWhenSnapshotExists() {
        long assetId = seedAsset("SM4USDT");
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        marketBreadthSnapshotDao.upsert(new MarketBreadthSnapshot(Timeframe.D1, CLOSE_TIME, null, 3, 1, 0, 4, new BigDecimal("0.7500")));

        JavalinTest.test(appWithFixedClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)), (server, client) -> {
            String body = client.get("/api/v1/summary?timeframe=D1&format=markdown").body().string();
            assertThat(body).contains("## Market Pulse").contains("**Bullish:** 3").contains("75.00%");
        });
    }

    @Test
    void watchlistFormatOmitsRsiSectionsAndUnflippedAssets() {
        long assetId = seedAsset("SM5USDT");
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL));

        JavalinTest.test(appWithFixedClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)), (server, client) -> {
            Response response = client.get("/api/v1/summary?timeframe=D1&format=WATCHLIST");
            assertThat(response.header("Content-Type")).contains("text/plain");
            String body = response.body().string();
            assertThat(body).contains("### Recent Bullish Flips").contains("SM5USDT")
                    .doesNotContain("Bearish").doesNotContain("RSI");
        });
    }

    @Test
    void missingRequiredTimeframeIsRejectedWith400() {
        JavalinTest.test(appWithFixedClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)), (server, client) ->
                assertThat(client.get("/api/v1/summary").code()).isEqualTo(400));
    }

    @Test
    void requireFreshRejectsStaleRequestedTimeframeWith503() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM candle WHERE timeframe = 'D1'");
        }
        Clock farPastGrace = Clock.fixed(NOW.plusHours(6).toInstant(), ZoneOffset.UTC);

        JavalinTest.test(appWithFixedClock(farPastGrace), (server, client) -> {
            Response response = client.get("/api/v1/summary?timeframe=D1&requireFresh=true");
            assertThat(response.code()).isEqualTo(503);
            assertThat(response.header("Retry-After")).isNotNull();
        });
    }

    private static void deleteD1MarketBreadthSnapshots() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM market_breadth_snapshot WHERE timeframe = 'D1'");
        }
    }

    @Test
    void assetClassFilterRestrictsSignalsAndIsEchoedInMarkdown() {
        long crypto = seedAsset("SM6AUSDT", AssetClass.CRYPTO);
        long stock = seedAsset("SM6BSTOCK", AssetClass.STOCK);
        signalStateDao.upsert(new SignalState(crypto, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL));
        signalStateDao.upsert(new SignalState(stock, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL));

        JavalinTest.test(appWithFixedClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)), (server, client) -> {
            Response jsonResponse = client.get("/api/v1/summary?timeframe=D1&assetClass=STOCK");
            String jsonBody = jsonResponse.body().string();
            assertThat(jsonBody).contains("SM6BSTOCK").doesNotContain("SM6AUSDT").contains("\"assetClass\":\"STOCK\"");

            String markdownBody = client.get("/api/v1/summary?timeframe=D1&assetClass=STOCK&format=markdown").body().string();
            assertThat(markdownBody).contains("**Asset Class:** STOCK");
        });
    }

    @Test
    void pulseRespectsTheAssetClassFilterInsteadOfAlwaysReflectingEveryClassCombined() {
        marketBreadthSnapshotDao.upsert(new MarketBreadthSnapshot(Timeframe.D1, CLOSE_TIME, null, 10, 5, 0, 15, new BigDecimal("0.6667")));
        marketBreadthSnapshotDao.upsert(new MarketBreadthSnapshot(Timeframe.D1, CLOSE_TIME, AssetClass.STOCK, 2, 8, 0, 10, new BigDecimal("0.2000")));

        JavalinTest.test(appWithFixedClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)), (server, client) -> {
            String combinedBody = client.get("/api/v1/summary?timeframe=D1").body().string();
            assertThat(combinedBody).contains("\"bullishCount\":10");

            String stockBody = client.get("/api/v1/summary?timeframe=D1&assetClass=STOCK").body().string();
            assertThat(stockBody).contains("\"bullishCount\":2").doesNotContain("\"bullishCount\":10");
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
                BigDecimal.valueOf(100), BigDecimal.valueOf(110), BigDecimal.valueOf(90), BigDecimal.valueOf(105), BigDecimal.valueOf(1000)));
    }
}
