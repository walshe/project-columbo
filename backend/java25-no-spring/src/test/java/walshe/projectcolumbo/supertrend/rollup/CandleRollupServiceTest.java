package walshe.projectcolumbo.supertrend.rollup;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.persistence.Asset;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.persistence.SchemaMigrator;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class CandleRollupServiceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    // Base Monday - each test offsets from here by a distinct .plusWeeks(N), so tests can run
    // in any order without their week groupings overlapping.
    private static final OffsetDateTime MONDAY_WEEK_1 = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    static DataSource dataSource;
    static AssetDao assetDao;
    static CandleDao candleDao;
    private static final AtomicLong SYMBOL_COUNTER = new AtomicLong();

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
    void completeFinalizedWeekProducesOneW1Candle() {
        long assetId = seedAsset();
        seedDailyCandles(assetId, MONDAY_WEEK_1, 7);
        // "Now" well after the week closes, so all 7 days are finalized.
        Clock clock = Clock.fixed(Instant.from(MONDAY_WEEK_1.plusDays(30)), ZoneOffset.UTC);

        new CandleRollupService(assetDao, candleDao, clock).rollupForAllActiveAssets();

        List<Candle> weeklyCandles = candleDao.findByAssetAndTimeframe(assetId, Timeframe.W1);
        assertThat(weeklyCandles).hasSize(1);
        Candle week = weeklyCandles.get(0);
        assertThat(week.open()).isEqualByComparingTo(dayOpen(0));
        assertThat(week.close()).isEqualByComparingTo(dayClose(6));
        assertThat(week.high()).isEqualByComparingTo(dayHigh(6)); // highest day's high, per fixture below
        assertThat(week.low()).isEqualByComparingTo(dayLow(0));   // lowest day's low, per fixture below
        assertThat(week.volume()).isEqualByComparingTo(sumOfDailyVolumes(7));
    }

    @Test
    void incompleteWeekProducesNoW1Candle() {
        long assetId = seedAsset();
        OffsetDateTime monday = MONDAY_WEEK_1.plusWeeks(1);
        seedDailyCandles(assetId, monday, 6); // only 6 of 7 days
        Clock clock = Clock.fixed(Instant.from(monday.plusDays(30)), ZoneOffset.UTC);

        new CandleRollupService(assetDao, candleDao, clock).rollupForAllActiveAssets();

        assertThat(candleDao.findByAssetAndTimeframe(assetId, Timeframe.W1)).isEmpty();
    }

    @Test
    void unfinalizedCurrentWeekProducesNoW1Candle() {
        long assetId = seedAsset();
        OffsetDateTime monday = MONDAY_WEEK_1.plusWeeks(2);
        seedDailyCandles(assetId, monday, 7);
        // "Now" is exactly the 7th day's close time - that candle is not yet finalized.
        Clock clock = Clock.fixed(Instant.from(monday.plusDays(7)), ZoneOffset.UTC);

        new CandleRollupService(assetDao, candleDao, clock).rollupForAllActiveAssets();

        assertThat(candleDao.findByAssetAndTimeframe(assetId, Timeframe.W1)).isEmpty();
    }

    @Test
    void reRollingUpAlreadyStoredWeekIsANoOp() {
        long assetId = seedAsset();
        OffsetDateTime monday = MONDAY_WEEK_1.plusWeeks(3);
        seedDailyCandles(assetId, monday, 7);
        Clock clock = Clock.fixed(Instant.from(monday.plusDays(30)), ZoneOffset.UTC);
        CandleRollupService service = new CandleRollupService(assetDao, candleDao, clock);

        service.rollupForAllActiveAssets();
        service.rollupForAllActiveAssets();

        assertThat(candleDao.findByAssetAndTimeframe(assetId, Timeframe.W1)).hasSize(1);
    }

    private static long seedAsset() {
        String symbol = "ROLLUP" + SYMBOL_COUNTER.incrementAndGet() + "USDT";
        insertAsset(symbol);
        return assetDao.findAllActive().stream()
                .filter(a -> a.symbol().equals(symbol))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private static void insertAsset(String symbol) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO asset (symbol, provider, active) VALUES (?, ?::provider, true)")) {
            statement.setString(1, symbol);
            statement.setString(2, Provider.BINANCE.name());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void seedDailyCandles(long assetId, OffsetDateTime weekStart, int dayCount) {
        for (int i = 0; i < dayCount; i++) {
            OffsetDateTime closeTime = weekStart.plusDays(i + 1);
            Candle candle = new Candle(
                    closeTime.minusDays(1),
                    closeTime,
                    Timeframe.D1,
                    dayOpen(i),
                    dayHigh(i),
                    dayLow(i),
                    dayClose(i),
                    dayVolume(i)
            );
            candleDao.upsert(assetId, candle);
        }
    }

    // Fixture values chosen so day 6 (index 6, the last day) has the highest high and day 0 has
    // the lowest low - makes the expected aggregate high/low unambiguous in assertions above.
    private static BigDecimal dayOpen(int dayIndex) {
        return BigDecimal.valueOf(100 + dayIndex);
    }

    private static BigDecimal dayHigh(int dayIndex) {
        return BigDecimal.valueOf(110 + dayIndex);
    }

    private static BigDecimal dayLow(int dayIndex) {
        return BigDecimal.valueOf(90 + dayIndex);
    }

    private static BigDecimal dayClose(int dayIndex) {
        return BigDecimal.valueOf(105 + dayIndex);
    }

    private static BigDecimal dayVolume(int dayIndex) {
        return BigDecimal.valueOf(1000 + dayIndex);
    }

    private static BigDecimal sumOfDailyVolumes(int dayCount) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < dayCount; i++) {
            sum = sum.add(dayVolume(i));
        }
        return sum;
    }
}
