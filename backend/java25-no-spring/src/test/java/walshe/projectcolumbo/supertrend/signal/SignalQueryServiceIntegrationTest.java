package walshe.projectcolumbo.supertrend.signal;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

// The 60 migration-seeded assets are deactivated in @BeforeEach so each test's results only
// reflect the assets it explicitly seeds - same pattern as MarketBreadthPulseServiceIntegrationTest.
@Testcontainers
class SignalQueryServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime LATEST_CLOSE = OffsetDateTime.of(2024, 1, 10, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime FLIP_CLOSE = OffsetDateTime.of(2024, 1, 5, 0, 0, 0, 0, ZoneOffset.UTC);

    static DataSource dataSource;
    static AssetDao assetDao;
    static CandleDao candleDao;
    static SignalStateDao signalStateDao;
    static AssetLiquidityDao assetLiquidityDao;
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
        signalQueryService = new SignalQueryService(assetDao, signalStateDao, candleDao, assetLiquidityDao);
    }

    @BeforeEach
    void deactivateSeededAssets() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE asset SET active = false");
        }
    }

    @Test
    void enrichesWithPctChangeSinceFlipFromRealCandleCloses() {
        long assetId = seedAsset("SQ1USDT");
        seedCandle(assetId, FLIP_CLOSE, new BigDecimal("100.00"));
        seedCandle(assetId, LATEST_CLOSE, new BigDecimal("115.00"));
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, FLIP_CLOSE, TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL));
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, LATEST_CLOSE, TrendState.BULLISH, SignalEvent.NONE));

        var results = signalQueryService.listSignals(Timeframe.D1, null, null);

        assertThat(results).hasSize(1);
        SignalSummary summary = results.get(0);
        assertThat(summary.symbol()).isEqualTo("SQ1USDT");
        assertThat(summary.trendState()).isEqualTo(TrendState.BULLISH);
        // lastFlipTime is reported as the flip candle's open time, not its close time (see
        // Timeframe#openTimeFor) - D1's span is 1 day, and Binance's close = open + span - 1ms
        // convention means the exact open time is FLIP_CLOSE minus a day plus that 1ms back.
        assertThat(summary.lastFlipTime()).isEqualTo(FLIP_CLOSE.minusDays(1).plusNanos(1_000_000));
        assertThat(summary.pctChangeSinceFlip()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(summary.tradingviewUrl()).isEqualTo("https://www.tradingview.com/chart/?symbol=BINANCE%3ASQ1USDT&interval=1D");
    }

    @Test
    void avgVolume7dReflectsRecentCandleVolumeFromTheLiquidityView() {
        long assetId = seedAsset("SQ1LUSDT");
        OffsetDateTime recentClose = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        seedCandle(assetId, recentClose, new BigDecimal("50.00"), new BigDecimal("777"));
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, recentClose, TrendState.BULLISH, SignalEvent.NONE));

        var results = signalQueryService.listSignals(Timeframe.D1, null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).avgVolume7d()).isEqualByComparingTo(new BigDecimal("777"));
    }

    @Test
    void assetWithNoRecordedFlipHasNullFlipFieldsButStillAppears() {
        long assetId = seedAsset("SQ2USDT");
        seedCandle(assetId, LATEST_CLOSE, new BigDecimal("50.00"));
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, LATEST_CLOSE, TrendState.BEARISH, SignalEvent.NONE));

        var results = signalQueryService.listSignals(Timeframe.D1, null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).lastFlipTime()).isNull();
        assertThat(results.get(0).pctChangeSinceFlip()).isNull();
    }

    @Test
    void stateFilterIsAppliedAgainstRealData() {
        long bullish = seedAsset("SQ3AUSDT");
        long bearish = seedAsset("SQ3BUSDT");
        signalStateDao.upsert(new SignalState(bullish, Timeframe.D1, LATEST_CLOSE, TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(bearish, Timeframe.D1, LATEST_CLOSE, TrendState.BEARISH, SignalEvent.NONE));

        var results = signalQueryService.listSignals(Timeframe.D1, TrendState.BEARISH, null);

        assertThat(results).extracting(SignalSummary::symbol).containsExactly("SQ3BUSDT");
    }

    @Test
    void deactivatedAssetsAreExcluded() {
        long deactivated = seedAsset("SQ4USDT");
        signalStateDao.upsert(new SignalState(deactivated, Timeframe.D1, LATEST_CLOSE, TrendState.BULLISH, SignalEvent.NONE));
        assetDao.deactivate(deactivated);

        var results = signalQueryService.listSignals(Timeframe.D1, null, null);

        assertThat(results).isEmpty();
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

    private static void seedCandle(long assetId, OffsetDateTime closeTime, BigDecimal close) {
        seedCandle(assetId, closeTime, close, BigDecimal.valueOf(1000));
    }

    private static void seedCandle(long assetId, OffsetDateTime closeTime, BigDecimal close, BigDecimal volume) {
        candleDao.upsert(assetId, new Candle(
                closeTime.minusDays(1), closeTime, Timeframe.D1,
                close, close, close, close, volume));
    }
}
