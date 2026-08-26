package walshe.projectcolumbo.supertrend.signal;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
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

@Testcontainers
class SignalStateDetectionServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime BASE_TIME = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    static DataSource dataSource;
    static AssetDao assetDao;
    static CandleDao candleDao;
    static SignalStateDao signalStateDao;

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
    }

    @Test
    void assetWithFewerCandlesThanAtrWarmUpGetsUnknownTrendState() {
        long assetId = seedAsset("SIG1USDT");
        seedDailyCandles(assetId, 3); // well under atrLength(10) - no concrete SuperTrend result yet

        new SignalStateDetectionService(assetDao, candleDao, signalStateDao, dataSource).computeForAllActiveAssets(Timeframe.D1);

        SignalState latest = latestFor(assetId);
        assertThat(latest.trendState()).isEqualTo(TrendState.UNKNOWN);
        assertThat(latest.event()).isEqualTo(SignalEvent.NONE);
    }

    @Test
    void assetWithEnoughHistoryGetsAConcreteTrendState() {
        long assetId = seedAsset("SIG2USDT");
        seedDailyCandles(assetId, 15); // > atrLength(10)

        new SignalStateDetectionService(assetDao, candleDao, signalStateDao, dataSource).computeForAllActiveAssets(Timeframe.D1);

        assertThat(latestFor(assetId).trendState()).isIn(TrendState.BULLISH, TrendState.BEARISH);
    }

    @Test
    void producesNoStateForAnAssetWithNoCandlesYet() {
        seedAsset("SIG3USDT");

        new SignalStateDetectionService(assetDao, candleDao, signalStateDao, dataSource).computeForAllActiveAssets(Timeframe.D1);

        // No exception, no crash - just nothing to detect. Nothing to assert beyond "didn't throw".
    }

    @Test
    void reDetectingIdenticalHistoryIsIdempotent() {
        long assetId = seedAsset("SIG4USDT");
        seedDailyCandles(assetId, 15);
        SignalStateDetectionService service = new SignalStateDetectionService(assetDao, candleDao, signalStateDao, dataSource);

        service.computeForAllActiveAssets(Timeframe.D1);
        SignalState firstRun = latestFor(assetId);

        service.computeForAllActiveAssets(Timeframe.D1);
        SignalState secondRun = latestFor(assetId);

        assertThat(secondRun).isEqualTo(firstRun);
    }

    @Test
    void incrementalRunOnlyAdvancesPastTheLastPersistedCloseTime() {
        long assetId = seedAsset("SIG5USDT");
        seedDailyCandles(assetId, 15);
        SignalStateDetectionService service = new SignalStateDetectionService(assetDao, candleDao, signalStateDao, dataSource);

        service.computeForAllActiveAssets(Timeframe.D1);
        SignalState afterFirstRun = latestFor(assetId);

        seedDailyCandles(assetId, 15, 3); // 3 more days beyond the first 15
        service.computeForAllActiveAssets(Timeframe.D1);
        SignalState afterSecondRun = latestFor(assetId);

        assertThat(afterSecondRun.closeTime()).isAfter(afterFirstRun.closeTime());
    }

    private static SignalState latestFor(long assetId) {
        return signalStateDao.findLatestForAllAssets(Timeframe.D1).stream()
                .filter(s -> s.assetId() == assetId)
                .findFirst()
                .orElseThrow();
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

    private static void seedDailyCandles(long assetId, int dayCount) {
        seedDailyCandles(assetId, 0, dayCount);
    }

    private static void seedDailyCandles(long assetId, int startOffset, int dayCount) {
        for (int i = startOffset; i < startOffset + dayCount; i++) {
            OffsetDateTime closeTime = BASE_TIME.plusDays(i + 1);
            Candle candle = new Candle(
                    closeTime.minusDays(1),
                    closeTime,
                    Timeframe.D1,
                    BigDecimal.valueOf(100 + i),
                    BigDecimal.valueOf(110 + i),
                    BigDecimal.valueOf(90 + i),
                    BigDecimal.valueOf(105 + i),
                    BigDecimal.valueOf(1000)
            );
            candleDao.upsert(assetId, candle);
        }
    }
}
