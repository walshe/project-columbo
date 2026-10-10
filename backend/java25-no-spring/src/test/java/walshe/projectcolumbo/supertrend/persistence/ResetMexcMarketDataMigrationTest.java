package walshe.projectcolumbo.supertrend.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.indicator.SuperTrendDirection;
import walshe.projectcolumbo.supertrend.indicator.SuperTrendResult;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.SignalEvent;
import walshe.projectcolumbo.supertrend.signal.SignalState;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** V27 deletes every MEXC asset's candles/indicators/signals so the next run re-backfills them, and nothing else. */
@Testcontainers
class ResetMexcMarketDataMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime OPEN = OffsetDateTime.of(2025, 11, 12, 0, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void deletesMexcMarketDataAndKeepsOtherProvidersAndBreadthHistory() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("26").load().migrate();

            long mexcAsset = insertAsset(dataSource, "V27MEXCUSDT", Provider.MEXC);
            long binanceAsset = insertAsset(dataSource, "V27BINUSDT", Provider.BINANCE);
            for (long assetId : new long[]{mexcAsset, binanceAsset}) {
                seedMarketData(dataSource, assetId);
            }
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO market_breadth_snapshot (timeframe, snapshot_close_time, bullish_count, bearish_count, missing_count, total_assets, bullish_ratio)
                        VALUES ('D1', '2025-11-12T23:59:59.999Z', 1, 1, 0, 2, 0.5)""");
            }

            SchemaMigrator.migrate(dataSource);

            assertThat(rowCount(dataSource, "candle", mexcAsset)).isZero();
            assertThat(rowCount(dataSource, "indicator_supertrend", mexcAsset)).isZero();
            assertThat(rowCount(dataSource, "signal_state", mexcAsset)).isZero();
            assertThat(rowCount(dataSource, "candle", binanceAsset)).isEqualTo(2); // D1 + W1
            assertThat(rowCount(dataSource, "indicator_supertrend", binanceAsset)).isEqualTo(1);
            assertThat(rowCount(dataSource, "signal_state", binanceAsset)).isEqualTo(1);
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
                 var resultSet = statement.executeQuery("SELECT count(*) FROM market_breadth_snapshot")) {
                resultSet.next();
                assertThat(resultSet.getLong(1)).isEqualTo(1);
            }
        }
    }

    private static void seedMarketData(HikariDataSource dataSource, long assetId) {
        CandleDao candleDao = new CandleDao(dataSource);
        OffsetDateTime close = OPEN.plusDays(1).minusNanos(1_000_000);
        candleDao.upsert(assetId, new Candle(OPEN, close, Timeframe.D1, BigDecimal.ONE, BigDecimal.TWO, BigDecimal.ONE, BigDecimal.TWO, BigDecimal.TEN));
        candleDao.upsert(assetId, new Candle(OPEN.minusDays(2), OPEN.plusDays(5).minusNanos(1_000_000), Timeframe.W1,
                BigDecimal.ONE, BigDecimal.TWO, BigDecimal.ONE, BigDecimal.TWO, BigDecimal.TEN));
        new SuperTrendIndicatorDao(dataSource).upsert(assetId, Timeframe.D1, new SuperTrendResult(close, BigDecimal.ONE,
                BigDecimal.TWO, BigDecimal.ONE, BigDecimal.ONE, SuperTrendDirection.UP));
        new SignalStateDao(dataSource).upsert(new SignalState(assetId, Timeframe.D1, close, TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL));
    }

    private static long insertAsset(HikariDataSource dataSource, String symbol, Provider provider) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO asset (symbol, provider, active) VALUES (?, ?::provider, true) RETURNING id")) {
            statement.setString(1, symbol);
            statement.setString(2, provider.name());
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static long rowCount(HikariDataSource dataSource, String table, long assetId) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT count(*) FROM " + table + " WHERE asset_id = ?")) {
            statement.setLong(1, assetId);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }
}
