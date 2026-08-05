package walshe.projectcolumbo.supertrend.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.indicator.SuperTrendDirection;
import walshe.projectcolumbo.supertrend.indicator.SuperTrendResult;
import walshe.projectcolumbo.supertrend.pipeline.IngestionRunOutcome;
import walshe.projectcolumbo.supertrend.pipeline.IngestionRunStatus;
import walshe.projectcolumbo.supertrend.pulse.MarketBreadthSnapshot;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.SignalEvent;
import walshe.projectcolumbo.supertrend.signal.SignalState;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Tests share one migrated schema/container for speed (avoids spinning up 8 containers), so
// execution order matters where a test mutates shared state (e.g. deactivating an asset) that
// a later test's assertions depend on not having happened yet.
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static DataSource dataSource;

    @BeforeAll
    static void migrate() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
        SchemaMigrator.migrate(dataSource);
    }

    @Test
    @Order(1)
    void assetDaoFindsAllSeededActiveAssets() {
        List<Asset> assets = new AssetDao(dataSource).findAllActive();

        assertThat(assets).hasSize(200); // 60 crypto (V8) + 140 stock/ETF (V12)
        assertThat(assets).allSatisfy(a -> assertThat(a.active()).isTrue());
    }

    @Test
    @Order(2)
    void assetDaoDeactivateRemovesAssetFromActiveList() {
        AssetDao assetDao = new AssetDao(dataSource);
        Asset asset = assetDao.findAllActive().get(0);

        assetDao.deactivate(asset.id());

        assertThat(assetDao.findAllActive()).extracting(Asset::id).doesNotContain(asset.id());
    }

    @Test
    @Order(3)
    void candleDaoUpsertIsIdempotentAndUpdatesOnRevision() {
        CandleDao candleDao = new CandleDao(dataSource);
        long assetId = new AssetDao(dataSource).findAllActive().get(1).id();
        Candle candle = candle(1, "100", "110", "90", "105");

        assertThat(candleDao.upsert(assetId, candle)).isEqualTo(UpsertOutcome.INSERTED);
        assertThat(candleDao.upsert(assetId, candle)).isEqualTo(UpsertOutcome.UNCHANGED);

        Candle revised = candle(1, "100", "110", "90", "106");
        assertThat(candleDao.upsert(assetId, revised)).isEqualTo(UpsertOutcome.UPDATED);

        List<Candle> stored = candleDao.findByAssetAndTimeframe(assetId, Timeframe.D1);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).close()).isEqualByComparingTo("106");
    }

    @Test
    @Order(4)
    void candleDaoFindsLatestCloseTime() {
        CandleDao candleDao = new CandleDao(dataSource);
        long assetId = new AssetDao(dataSource).findAllActive().get(2).id();

        assertThat(candleDao.findLatestCloseTime(assetId, Timeframe.D1)).isEmpty();

        candleDao.upsert(assetId, candle(1, "1", "2", "0.5", "1.5"));
        candleDao.upsert(assetId, candle(2, "1", "2", "0.5", "1.5"));

        assertThat(candleDao.findLatestCloseTime(assetId, Timeframe.D1)).contains(closeTime(2));
    }

    @Test
    @Order(5)
    void superTrendIndicatorDaoUpsertIsIdempotentAndUpdatesOnRevision() {
        SuperTrendIndicatorDao dao = new SuperTrendIndicatorDao(dataSource);
        long assetId = new AssetDao(dataSource).findAllActive().get(3).id();
        SuperTrendResult result = superTrendResult(1, "2.0", "110", "90", "90", SuperTrendDirection.UP);

        assertThat(dao.upsert(assetId, Timeframe.D1, result)).isEqualTo(UpsertOutcome.INSERTED);
        assertThat(dao.upsert(assetId, Timeframe.D1, result)).isEqualTo(UpsertOutcome.UNCHANGED);

        SuperTrendResult revised = superTrendResult(1, "2.5", "111", "91", "91", SuperTrendDirection.UP);
        assertThat(dao.upsert(assetId, Timeframe.D1, revised)).isEqualTo(UpsertOutcome.UPDATED);

        assertThat(dao.findLatestCloseTime(assetId, Timeframe.D1)).contains(closeTime(1));
    }

    @Test
    @Order(6)
    void signalStateDaoUpsertAndQueries() {
        SignalStateDao dao = new SignalStateDao(dataSource);
        long assetId = new AssetDao(dataSource).findAllActive().get(4).id();

        dao.upsert(new SignalState(assetId, Timeframe.D1, closeTime(1), TrendState.BEARISH, SignalEvent.NONE));
        dao.upsert(new SignalState(assetId, Timeframe.D1, closeTime(2), TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL));

        List<SignalState> latestPerAsset = dao.findLatestForAllAssets(Timeframe.D1);
        assertThat(latestPerAsset).anySatisfy(s -> {
            assertThat(s.assetId()).isEqualTo(assetId);
            assertThat(s.trendState()).isEqualTo(TrendState.BULLISH);
        });

        Optional<SignalState> flip = dao.findLatestFlipForAsset(assetId, Timeframe.D1);
        assertThat(flip).isPresent();
        assertThat(flip.get().event()).isEqualTo(SignalEvent.BULLISH_REVERSAL);
    }

    @Test
    @Order(7)
    void marketBreadthSnapshotDaoUpsertAndQueries() {
        MarketBreadthSnapshotDao dao = new MarketBreadthSnapshotDao(dataSource);
        MarketBreadthSnapshot snapshot = new MarketBreadthSnapshot(Timeframe.D1, closeTime(1), null, 30, 25, 5, 60, new BigDecimal("0.5455"));

        dao.upsert(snapshot);

        assertThat(dao.findLatest(Timeframe.D1)).isPresent();
        assertThat(dao.findRange(Timeframe.D1, closeTime(0), closeTime(2))).hasSize(1);
    }

    @Test
    @Order(8)
    void ingestionRunDaoLifecycle() {
        IngestionRunDao dao = new IngestionRunDao(dataSource);

        assertThat(dao.isRunning(Provider.BINANCE, Timeframe.D1)).isFalse();

        long runId = dao.start(Provider.BINANCE, Timeframe.D1, 60, OffsetDateTime.now(ZoneOffset.UTC));
        assertThat(dao.isRunning(Provider.BINANCE, Timeframe.D1)).isTrue();

        dao.complete(runId, new IngestionRunOutcome(IngestionRunStatus.SUCCESS, OffsetDateTime.now(ZoneOffset.UTC), 1234, 60, 0, 0, 0, null));
        assertThat(dao.isRunning(Provider.BINANCE, Timeframe.D1)).isFalse();
    }

    private static Candle candle(int dayOffset, String open, String high, String low, String close) {
        return new Candle(
                closeTime(dayOffset).minusDays(1),
                closeTime(dayOffset),
                Timeframe.D1,
                new BigDecimal(open),
                new BigDecimal(high),
                new BigDecimal(low),
                new BigDecimal(close),
                BigDecimal.ZERO
        );
    }

    private static SuperTrendResult superTrendResult(int dayOffset, String atr, String upperBand, String lowerBand, String superTrend, SuperTrendDirection direction) {
        return new SuperTrendResult(closeTime(dayOffset), new BigDecimal(atr), new BigDecimal(upperBand), new BigDecimal(lowerBand), new BigDecimal(superTrend), direction);
    }

    private static OffsetDateTime closeTime(int dayOffset) {
        return OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).plusDays(dayOffset);
    }
}
