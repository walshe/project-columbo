package walshe.projectcolumbo.supertrend.signal;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.AssetLiquidityDao;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.persistence.SchemaMigrator;
import walshe.projectcolumbo.supertrend.persistence.SignalStateDao;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ScanServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime NOW = OffsetDateTime.of(2024, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime CLOSE_TIME = OffsetDateTime.of(2024, 1, 14, 0, 0, 0, 0, ZoneOffset.UTC);

    static DataSource dataSource;
    static AssetDao assetDao;
    static SignalStateDao signalStateDao;
    static ScanService scanService;

    @BeforeAll
    static void migrate() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
        SchemaMigrator.migrate(dataSource);
        assetDao = new AssetDao(dataSource);
        CandleDao candleDao = new CandleDao(dataSource);
        signalStateDao = new SignalStateDao(dataSource);
        AssetLiquidityDao assetLiquidityDao = new AssetLiquidityDao(dataSource);
        SignalQueryService signalQueryService = new SignalQueryService(assetDao, signalStateDao, candleDao, assetLiquidityDao);
        scanService = new ScanService(signalQueryService, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
    }

    @BeforeEach
    void deactivateSeededAssets() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE asset SET active = false");
        }
    }

    @Test
    void matchesRealSignalStateAcrossTimeframesWithAnd() {
        long assetId = seedAsset("SC1USDT");
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(assetId, Timeframe.W1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));

        ScanRequest request = new ScanRequest(ScanOperator.AND, List.of(
                new ScanCondition(Timeframe.D1, TrendState.BULLISH, null),
                new ScanCondition(Timeframe.W1, TrendState.BULLISH, null)
        ), null, null);

        List<ScanResult> results = scanService.execute(request);

        assertThat(results).extracting(ScanResult::symbol).containsExactly("SC1USDT");
        assertThat(results.get(0).matchedConditions())
                .extracting(ScanConditionMatch::tradingviewUrl)
                .containsExactly(
                        "https://www.tradingview.com/chart/?symbol=BINANCE%3ASC1USDT&interval=1D",
                        "https://www.tradingview.com/chart/?symbol=BINANCE%3ASC1USDT&interval=1W");
    }

    @Test
    void deactivatedAssetsAreExcluded() {
        long assetId = seedAsset("SC2USDT");
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        assetDao.deactivate(assetId);

        ScanRequest request = new ScanRequest(ScanOperator.AND, List.of(
                new ScanCondition(Timeframe.D1, TrendState.BULLISH, null)
        ), null, null);

        assertThat(scanService.execute(request)).isEmpty();
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
}
