package walshe.projectcolumbo.supertrend.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class CandleDaoIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime BASE_TIME = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    static DataSource dataSource;
    static AssetDao assetDao;
    static CandleDao candleDao;

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
    }

    @Test
    void windowForIncrementalReturnsExactlyTheWarmUpWindowPlusEverythingFromTheAnchorWhenHistoryIsDeep() throws SQLException {
        long assetId = seedAsset("CDW1USDT");
        seedDailyCandles(assetId, 300);
        OffsetDateTime anchor = BASE_TIME.plusDays(250); // close time of the 250th candle
        int warmupBars = 100;

        try (Connection connection = dataSource.getConnection()) {
            List<Candle> window = candleDao.findWindowForIncremental(connection, assetId, Timeframe.D1, anchor, warmupBars);

            // exactly warmupBars candles before the anchor + the anchor itself + the 50 after it
            assertThat(window).hasSize(warmupBars + 1 + 50);
            assertThat(window.getFirst().closeTime()).isEqualTo(anchor.minusDays(warmupBars));
            assertThat(window.getLast().closeTime()).isEqualTo(BASE_TIME.plusDays(300));
            assertThat(window).isSortedAccordingTo((a, b) -> a.closeTime().compareTo(b.closeTime()));
        }
    }

    @Test
    void windowForIncrementalIncludesTheAnchorCandleItself() throws SQLException {
        long assetId = seedAsset("CDW2USDT");
        seedDailyCandles(assetId, 200);
        OffsetDateTime anchor = BASE_TIME.plusDays(150);

        try (Connection connection = dataSource.getConnection()) {
            List<Candle> window = candleDao.findWindowForIncremental(connection, assetId, Timeframe.D1, anchor, 100);

            assertThat(window).extracting(Candle::closeTime).contains(anchor);
        }
    }

    @Test
    void windowForIncrementalFallsBackToFullHistoryWhenFewerThanWarmUpBarsPrecedeTheAnchor() throws SQLException {
        long assetId = seedAsset("CDW3USDT");
        seedDailyCandles(assetId, 40);
        OffsetDateTime anchor = BASE_TIME.plusDays(35);

        try (Connection connection = dataSource.getConnection()) {
            List<Candle> window = candleDao.findWindowForIncremental(connection, assetId, Timeframe.D1, anchor, 100);

            assertThat(window).hasSize(40); // only 34 candles precede the anchor, so the whole history comes back
            assertThat(window.getFirst().closeTime()).isEqualTo(BASE_TIME.plusDays(1));
        }
    }

    @Test
    void windowForIncrementalIsEmptyForAnAssetWithNoCandles() throws SQLException {
        long assetId = seedAsset("CDW4USDT");

        try (Connection connection = dataSource.getConnection()) {
            assertThat(candleDao.findWindowForIncremental(connection, assetId, Timeframe.D1, BASE_TIME, 100)).isEmpty();
        }
    }

    @Test
    void windowForIncrementalIsScopedToTheRequestedTimeframe() throws SQLException {
        long assetId = seedAsset("CDW5USDT");
        seedDailyCandles(assetId, 20);

        try (Connection connection = dataSource.getConnection()) {
            assertThat(candleDao.findWindowForIncremental(connection, assetId, Timeframe.W1, BASE_TIME.plusDays(10), 100)).isEmpty();
        }
    }

    @Test
    void findLatestCloseTimeConnectionOverloadAgreesWithTheNoArgOne() throws SQLException {
        long assetId = seedAsset("CDW6USDT");
        seedDailyCandles(assetId, 12);

        try (Connection connection = dataSource.getConnection()) {
            assertThat(candleDao.findLatestCloseTime(connection, assetId, Timeframe.D1))
                    .isEqualTo(candleDao.findLatestCloseTime(assetId, Timeframe.D1))
                    .hasValue(BASE_TIME.plusDays(12));
        }
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
        return assetDao.findAllActive().stream()
                .filter(a -> a.symbol().equals(symbol))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private static void seedDailyCandles(long assetId, int dayCount) {
        for (int i = 0; i < dayCount; i++) {
            OffsetDateTime closeTime = BASE_TIME.plusDays(i + 1);
            candleDao.upsert(assetId, new Candle(
                    closeTime.minusDays(1),
                    closeTime,
                    Timeframe.D1,
                    BigDecimal.valueOf(100 + i),
                    BigDecimal.valueOf(110 + i),
                    BigDecimal.valueOf(90 + i),
                    BigDecimal.valueOf(105 + i),
                    BigDecimal.valueOf(1000)
            ));
        }
    }
}
