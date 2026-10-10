package walshe.projectcolumbo.supertrend.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import walshe.projectcolumbo.supertrend.indicator.SuperTrendDirection;
import walshe.projectcolumbo.supertrend.indicator.SuperTrendResult;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.SignalEvent;
import walshe.projectcolumbo.supertrend.signal.SignalState;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Range reads backing the {@code include=supertrend} chart overlay. */
@Testcontainers
class ChartOverlayDaoIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime BASE_TIME = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    static DataSource dataSource;
    static AssetDao assetDao;
    static SuperTrendIndicatorDao superTrendIndicatorDao;
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
        superTrendIndicatorDao = new SuperTrendIndicatorDao(dataSource);
        signalStateDao = new SignalStateDao(dataSource);
    }

    @Test
    void superTrendBetweenIsInclusiveOnBothEndsAndAscending() throws SQLException {
        long assetId = seedAsset("COD1USDT");
        for (int i = 1; i <= 10; i++) {
            superTrendIndicatorDao.upsert(assetId, Timeframe.D1, superTrend(BASE_TIME.plusDays(i), i % 2 == 0 ? SuperTrendDirection.UP : SuperTrendDirection.DOWN));
        }

        try (Connection connection = dataSource.getConnection()) {
            List<SuperTrendResult> results = superTrendIndicatorDao.findBetween(connection, assetId, Timeframe.D1,
                    BASE_TIME.plusDays(3), BASE_TIME.plusDays(6));

            assertThat(results).extracting(SuperTrendResult::closeTime)
                    .containsExactly(BASE_TIME.plusDays(3), BASE_TIME.plusDays(4), BASE_TIME.plusDays(5), BASE_TIME.plusDays(6));
            assertThat(superTrendIndicatorDao.findBetween(connection, assetId, Timeframe.W1, BASE_TIME, BASE_TIME.plusDays(10))).isEmpty();
        }
    }

    @Test
    void flipsBetweenExcludesNoneEventsAndRowsOutsideTheRange() throws SQLException {
        long assetId = seedAsset("COD2USDT");
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, BASE_TIME.plusDays(1), TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL));
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, BASE_TIME.plusDays(2), TrendState.BULLISH, SignalEvent.NONE));
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, BASE_TIME.plusDays(3), TrendState.BEARISH, SignalEvent.BEARISH_REVERSAL));
        signalStateDao.upsert(new SignalState(assetId, Timeframe.D1, BASE_TIME.plusDays(9), TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL));

        try (Connection connection = dataSource.getConnection()) {
            List<SignalState> flips = signalStateDao.findFlipsBetween(connection, assetId, Timeframe.D1,
                    BASE_TIME.plusDays(1), BASE_TIME.plusDays(5));

            assertThat(flips).extracting(SignalState::event)
                    .containsExactly(SignalEvent.BULLISH_REVERSAL, SignalEvent.BEARISH_REVERSAL);
            assertThat(flips).extracting(SignalState::closeTime)
                    .containsExactly(BASE_TIME.plusDays(1), BASE_TIME.plusDays(3));
        }
    }

    @Test
    void findActiveBySymbolIgnoresInactiveAssetsAndMatchesCaseSensitively() throws SQLException {
        long activeId = seedAsset("COD3USDT");
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO asset (symbol, provider, active) VALUES (?, ?::provider, false)")) {
            statement.setString(1, "COD3USDT");
            statement.setString(2, Provider.MEXC.name());
            statement.executeUpdate();
        }

        assertThat(assetDao.findActiveBySymbol("COD3USDT")).extracting(a -> a.id()).containsExactly(activeId);
        assertThat(assetDao.findActiveBySymbol("cod3usdt")).isEmpty();
    }

    private static SuperTrendResult superTrend(OffsetDateTime closeTime, SuperTrendDirection direction) {
        return new SuperTrendResult(closeTime, BigDecimal.ONE, BigDecimal.valueOf(110), BigDecimal.valueOf(90),
                direction == SuperTrendDirection.UP ? BigDecimal.valueOf(90) : BigDecimal.valueOf(110), direction);
    }

    private static long seedAsset(String symbol) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO asset (symbol, provider, active) VALUES (?, ?::provider, true)")) {
            statement.setString(1, symbol);
            statement.setString(2, Provider.BINANCE.name());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return assetDao.findActiveBySymbol(symbol).getFirst().id();
    }
}
