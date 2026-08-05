package walshe.projectcolumbo.supertrend.pulse;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.MarketBreadthSnapshotDao;
import walshe.projectcolumbo.supertrend.persistence.SchemaMigrator;
import walshe.projectcolumbo.supertrend.persistence.SignalStateDao;
import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.SignalEvent;
import walshe.projectcolumbo.supertrend.signal.SignalState;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

// The 60 migration-seeded assets are deactivated in @BeforeEach so each test's counts only
// reflect the assets it explicitly seeds.
@Testcontainers
class MarketBreadthPulseServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime CLOSE_TIME = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    static DataSource dataSource;
    static AssetDao assetDao;
    static SignalStateDao signalStateDao;
    static MarketBreadthSnapshotDao marketBreadthSnapshotDao;

    @BeforeAll
    static void migrate() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
        SchemaMigrator.migrate(dataSource);
        assetDao = new AssetDao(dataSource);
        signalStateDao = new SignalStateDao(dataSource);
        marketBreadthSnapshotDao = new MarketBreadthSnapshotDao(dataSource);
    }

    @BeforeEach
    void deactivateSeededAssets() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE asset SET active = false");
        }
    }

    @Test
    void noSnapshotWhenNoSignalStateExistsYet() {
        seedAsset("PULSE1USDT", AssetClass.CRYPTO);

        new MarketBreadthPulseService(assetDao, signalStateDao, marketBreadthSnapshotDao).computeForAllActiveAssets(Timeframe.D1, null);

        assertThat(marketBreadthSnapshotDao.findLatest(Timeframe.D1)).isEmpty();
    }

    @Test
    void computesAndPersistsSnapshotFromSeededSignalStates() {
        long bullish1 = seedAsset("PULSE2AUSDT", AssetClass.CRYPTO);
        long bullish2 = seedAsset("PULSE2BUSDT", AssetClass.CRYPTO);
        long bearish = seedAsset("PULSE2CUSDT", AssetClass.CRYPTO);
        long unknown = seedAsset("PULSE2DUSDT", AssetClass.CRYPTO);
        seedAsset("PULSE2EUSDT", AssetClass.CRYPTO); // active, but never gets a signal_state row - counts as missing

        signalStateDao.upsert(new SignalState(bullish1, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(bullish2, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(bearish, Timeframe.D1, CLOSE_TIME, TrendState.BEARISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(unknown, Timeframe.D1, CLOSE_TIME, TrendState.UNKNOWN, SignalEvent.NONE));

        new MarketBreadthPulseService(assetDao, signalStateDao, marketBreadthSnapshotDao).computeForAllActiveAssets(Timeframe.D1, null);

        MarketBreadthSnapshot snapshot = marketBreadthSnapshotDao.findLatest(Timeframe.D1).orElseThrow();
        assertThat(snapshot.assetClass()).isNull();
        assertThat(snapshot.totalAssets()).isEqualTo(5);
        assertThat(snapshot.bullishCount()).isEqualTo(2);
        assertThat(snapshot.bearishCount()).isEqualTo(1);
        assertThat(snapshot.missingCount()).isEqualTo(2);
        assertThat(snapshot.bullishRatio()).isEqualByComparingTo(new BigDecimal("0.6667"));
        assertThat(snapshot.snapshotCloseTime()).isEqualTo(CLOSE_TIME);
    }

    @Test
    void deactivatedAssetsAreExcludedFromCounts() {
        long deactivated = seedAsset("PULSE3AUSDT", AssetClass.CRYPTO);
        long active = seedAsset("PULSE3BUSDT", AssetClass.CRYPTO);
        signalStateDao.upsert(new SignalState(deactivated, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(active, Timeframe.D1, CLOSE_TIME, TrendState.BEARISH, SignalEvent.NONE));
        assetDao.deactivate(deactivated);

        new MarketBreadthPulseService(assetDao, signalStateDao, marketBreadthSnapshotDao).computeForAllActiveAssets(Timeframe.D1, null);

        MarketBreadthSnapshot snapshot = marketBreadthSnapshotDao.findLatest(Timeframe.D1).orElseThrow();
        assertThat(snapshot.totalAssets()).isEqualTo(1);
        assertThat(snapshot.bullishCount()).isEqualTo(0);
        assertThat(snapshot.bearishCount()).isEqualTo(1);
    }

    @Test
    void perClassSnapshotOnlyReflectsThatClassesAssets() {
        long crypto = seedAsset("PULSE4AUSDT", AssetClass.CRYPTO);
        long stock = seedAsset("PULSE4BSTOCK", AssetClass.STOCK);
        signalStateDao.upsert(new SignalState(crypto, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(stock, Timeframe.D1, CLOSE_TIME, TrendState.BEARISH, SignalEvent.NONE));

        new MarketBreadthPulseService(assetDao, signalStateDao, marketBreadthSnapshotDao)
                .computeForAllActiveAssets(Timeframe.D1, AssetClass.STOCK);

        MarketBreadthSnapshot snapshot = marketBreadthSnapshotDao.findLatest(Timeframe.D1, AssetClass.STOCK).orElseThrow();
        assertThat(snapshot.assetClass()).isEqualTo(AssetClass.STOCK);
        assertThat(snapshot.totalAssets()).isEqualTo(1);
        assertThat(snapshot.bearishCount()).isEqualTo(1);
        assertThat(snapshot.bullishCount()).isEqualTo(0);
    }

    @Test
    void classWithNoActiveAssetsProducesNoSnapshot() {
        seedAsset("PULSE5USDT", AssetClass.CRYPTO);

        new MarketBreadthPulseService(assetDao, signalStateDao, marketBreadthSnapshotDao)
                .computeForAllActiveAssets(Timeframe.D1, AssetClass.COMMODITY);

        assertThat(marketBreadthSnapshotDao.findLatest(Timeframe.D1, AssetClass.COMMODITY)).isEmpty();
    }

    @Test
    void perClassAndCombinedSnapshotsCoexistIndependently() {
        long crypto = seedAsset("PULSE6AUSDT", AssetClass.CRYPTO);
        long stock = seedAsset("PULSE6BSTOCK", AssetClass.STOCK);
        signalStateDao.upsert(new SignalState(crypto, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(stock, Timeframe.D1, CLOSE_TIME, TrendState.BEARISH, SignalEvent.NONE));
        MarketBreadthPulseService service = new MarketBreadthPulseService(assetDao, signalStateDao, marketBreadthSnapshotDao);

        service.computeForAllActiveAssets(Timeframe.D1, null);
        service.computeForAllActiveAssets(Timeframe.D1, AssetClass.CRYPTO);
        service.computeForAllActiveAssets(Timeframe.D1, AssetClass.STOCK);

        assertThat(marketBreadthSnapshotDao.findLatest(Timeframe.D1).orElseThrow().totalAssets()).isEqualTo(2);
        assertThat(marketBreadthSnapshotDao.findLatest(Timeframe.D1, AssetClass.CRYPTO).orElseThrow().totalAssets()).isEqualTo(1);
        assertThat(marketBreadthSnapshotDao.findLatest(Timeframe.D1, AssetClass.STOCK).orElseThrow().totalAssets()).isEqualTo(1);
    }

    @Test
    void computeForAllActiveAssetsAndClassesProducesCombinedPlusEveryClassInOneCall() {
        long crypto = seedAsset("PULSE7AUSDT", AssetClass.CRYPTO);
        long stock = seedAsset("PULSE7BSTOCK", AssetClass.STOCK);
        long etf = seedAsset("PULSE7CETF", AssetClass.ETF);
        signalStateDao.upsert(new SignalState(crypto, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(stock, Timeframe.D1, CLOSE_TIME, TrendState.BEARISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(etf, Timeframe.D1, CLOSE_TIME, TrendState.BULLISH, SignalEvent.NONE));

        new MarketBreadthPulseService(assetDao, signalStateDao, marketBreadthSnapshotDao)
                .computeForAllActiveAssetsAndClasses(Timeframe.D1);

        assertThat(marketBreadthSnapshotDao.findLatest(Timeframe.D1).orElseThrow().totalAssets()).isEqualTo(3);
        assertThat(marketBreadthSnapshotDao.findLatest(Timeframe.D1, AssetClass.CRYPTO).orElseThrow().totalAssets()).isEqualTo(1);
        assertThat(marketBreadthSnapshotDao.findLatest(Timeframe.D1, AssetClass.STOCK).orElseThrow().totalAssets()).isEqualTo(1);
        assertThat(marketBreadthSnapshotDao.findLatest(Timeframe.D1, AssetClass.ETF).orElseThrow().totalAssets()).isEqualTo(1);
        assertThat(marketBreadthSnapshotDao.findLatest(Timeframe.D1, AssetClass.COMMODITY)).isEmpty();
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
}
