package walshe.projectcolumbo.supertrend.indicator;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.persistence.SchemaMigrator;
import walshe.projectcolumbo.supertrend.persistence.SuperTrendIndicatorDao;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class IndicatorComputationServiceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime BASE_TIME = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    static DataSource dataSource;
    static AssetDao assetDao;
    static CandleDao candleDao;
    static SuperTrendIndicatorDao superTrendIndicatorDao;

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
        superTrendIndicatorDao = new SuperTrendIndicatorDao(dataSource);
    }

    @Test
    void computesAndPersistsSuperTrendOnceEnoughHistoryExists() {
        long assetId = seedAsset("IND1USDT");
        seedDailyCandles(assetId, 15); // > atrLength(10), enough to produce results

        new IndicatorComputationService(assetDao, candleDao, superTrendIndicatorDao, dataSource)
                .computeForAllActiveAssets(Timeframe.D1);

        assertThat(superTrendIndicatorDao.findLatestCloseTime(assetId, Timeframe.D1)).isPresent();
    }

    @Test
    void producesNoResultForAnAssetWithNoCandlesYet() {
        seedAsset("IND2USDT");

        new IndicatorComputationService(assetDao, candleDao, superTrendIndicatorDao, dataSource)
                .computeForAllActiveAssets(Timeframe.D1);

        // No exception, no crash - just nothing to compute. Nothing to assert beyond "didn't throw".
    }

    @Test
    void incrementalRecomputeOnlyAddsNewResultsOnSecondRun() {
        long assetId = seedAsset("IND3USDT");
        seedDailyCandles(assetId, 12);
        IndicatorComputationService service = new IndicatorComputationService(assetDao, candleDao, superTrendIndicatorDao, dataSource);

        service.computeForAllActiveAssets(Timeframe.D1);
        OffsetDateTime firstLatest = superTrendIndicatorDao.findLatestCloseTime(assetId, Timeframe.D1).orElseThrow();

        seedDailyCandles(assetId, 12, 3); // 3 more days beyond the first 12
        service.computeForAllActiveAssets(Timeframe.D1);
        OffsetDateTime secondLatest = superTrendIndicatorDao.findLatestCloseTime(assetId, Timeframe.D1).orElseThrow();

        assertThat(secondLatest).isAfter(firstLatest);
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
