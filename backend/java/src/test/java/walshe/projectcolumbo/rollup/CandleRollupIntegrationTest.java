package walshe.projectcolumbo.rollup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import walshe.projectcolumbo.TestcontainersConfiguration;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.CandleRepository;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CandleRollupIntegrationTest {

    @Autowired
    private CandleRollupService candleRollupService;

    @Autowired
    private CandleRepository candleRepository;

    @Autowired
    private AssetRepository assetRepository;

    @BeforeEach
    void setUp() {
        candleRepository.deleteAll();
        assetRepository.deleteAll();
    }

    /**
     * Seeds N consecutive D1 candles for a given asset starting from the given Monday UTC.
     * Each candle has deterministic OHLCV based on its day index (0-based).
     * openTime  = startOfDay (midnight UTC on that day)
     * closeTime = openTime + 24h - 1 nanosecond (23:59:59.999999999 UTC)
     */
    private void seedD1Candles(Asset asset, OffsetDateTime mondayStart, int count) {
        for (int i = 0; i < count; i++) {
            OffsetDateTime openTime = mondayStart.plusDays(i);
            // 24 hours minus 1 nanosecond
            OffsetDateTime closeTime = openTime.plusHours(24).minusNanos(1);

            Candle candle = new Candle();
            candle.setAsset(asset);
            candle.setTimeframe(Timeframe.D1);
            candle.setOpenTime(openTime);
            candle.setCloseTime(closeTime);
            candle.setOpen(BigDecimal.valueOf(i));
            candle.setHigh(BigDecimal.valueOf(i + 5));
            candle.setLow(BigDecimal.valueOf(i));
            candle.setClose(BigDecimal.valueOf(i + 2));
            candle.setVolume(BigDecimal.valueOf(100));
            candle.setSource(MarketProvider.BINANCE);
            candleRepository.save(candle);
        }
    }

    /**
     * CNDL-05: Verify that W1 is a valid PostgreSQL enum value and that a W1 candle can
     * be persisted and read back from the real database after the V13 migration.
     * Also asserts OHLCV aggregation correctness end-to-end.
     */
    @Test
    void w1_timeframeExistsInDb() {
        // Monday 2025-01-06 UTC — an unambiguously past complete week
        OffsetDateTime mondayStart = OffsetDateTime.of(2025, 1, 6, 0, 0, 0, 0, ZoneOffset.UTC);

        Asset btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        assetRepository.save(btc);

        // Seed 7 D1 candles: day 0 (Monday) through day 6 (Sunday)
        seedD1Candles(btc, mondayStart, 7);

        // Execute the rollup
        candleRollupService.rollupForAllActiveAssets(Timeframe.D1, Timeframe.W1, DayOfWeek.MONDAY);

        // Assert exactly one W1 row persisted — if W1 were not a valid DB enum this would throw
        List<Candle> w1Candles = candleRepository.findByAssetAndTimeframe(btc, Timeframe.W1);
        assertThat(w1Candles).hasSize(1);

        Candle w1 = w1Candles.get(0);
        assertThat(w1.getTimeframe()).isEqualTo(Timeframe.W1);

        // open = Monday (day 0) open = BigDecimal(0)
        assertThat(w1.getOpen()).isEqualByComparingTo("0");
        // high = max daily high = day 6 high = 6+5 = 11
        assertThat(w1.getHigh()).isEqualByComparingTo("11");
        // low = min daily low = day 0 low = 0
        assertThat(w1.getLow()).isEqualByComparingTo("0");
        // close = Sunday (day 6) close = 6+2 = 8
        assertThat(w1.getClose()).isEqualByComparingTo("8");
        // volume = sum of 7 * 100 = 700
        assertThat(w1.getVolume()).isEqualByComparingTo("700");
    }

    /**
     * CNDL-03 (idempotency): Running the rollup twice for the same source data must not
     * produce duplicate W1 rows — the unique_asset_timeframe_close constraint and upsert
     * logic must hold across repeated runs.
     */
    @Test
    void rollup_isIdempotent() {
        OffsetDateTime mondayStart = OffsetDateTime.of(2025, 1, 6, 0, 0, 0, 0, ZoneOffset.UTC);

        Asset btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        assetRepository.save(btc);

        seedD1Candles(btc, mondayStart, 7);

        // First run
        candleRollupService.rollupForAllActiveAssets(Timeframe.D1, Timeframe.W1, DayOfWeek.MONDAY);

        // Second run — must produce no duplicates
        candleRollupService.rollupForAllActiveAssets(Timeframe.D1, Timeframe.W1, DayOfWeek.MONDAY);

        List<Candle> w1Candles = candleRepository.findByAssetAndTimeframe(btc, Timeframe.W1);
        assertThat(w1Candles).hasSize(1);
    }

    /**
     * CNDL-03 (incremental): After rolling up week A, adding D1 candles for week B and
     * re-running the rollup must produce exactly one new W1 candle for week B while
     * week A's W1 candle remains unchanged (same id and close_time).
     */
    @Test
    void rollup_isIncremental() {
        // Week A: Monday 2025-01-06 to Sunday 2025-01-12
        OffsetDateTime weekAStart = OffsetDateTime.of(2025, 1, 6, 0, 0, 0, 0, ZoneOffset.UTC);
        // Week B: Monday 2025-01-13 to Sunday 2025-01-19
        OffsetDateTime weekBStart = OffsetDateTime.of(2025, 1, 13, 0, 0, 0, 0, ZoneOffset.UTC);

        Asset btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        assetRepository.save(btc);

        // Seed and rollup week A
        seedD1Candles(btc, weekAStart, 7);
        candleRollupService.rollupForAllActiveAssets(Timeframe.D1, Timeframe.W1, DayOfWeek.MONDAY);

        List<Candle> afterFirstRollup = candleRepository.findByAssetAndTimeframe(btc, Timeframe.W1);
        assertThat(afterFirstRollup).hasSize(1);

        // Capture week A's W1 candle identity for comparison later
        Candle weekACandle = afterFirstRollup.get(0);
        Long weekAId = weekACandle.getId();
        OffsetDateTime weekACloseTime = weekACandle.getCloseTime();

        // Now seed week B and re-run the rollup
        seedD1Candles(btc, weekBStart, 7);
        candleRollupService.rollupForAllActiveAssets(Timeframe.D1, Timeframe.W1, DayOfWeek.MONDAY);

        // Assert exactly two W1 rows now exist
        List<Candle> afterSecondRollup = candleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(btc, Timeframe.W1);
        assertThat(afterSecondRollup).hasSize(2);

        // Week A's W1 candle must be unchanged — same id and close_time
        Candle weekAAfter = afterSecondRollup.get(0);
        assertThat(weekAAfter.getId()).isEqualTo(weekAId);
        assertThat(weekAAfter.getCloseTime()).isEqualTo(weekACloseTime);

        // Week B's W1 candle must have been newly created
        Candle weekBCandle = afterSecondRollup.get(1);
        assertThat(weekBCandle.getTimeframe()).isEqualTo(Timeframe.W1);
        assertThat(weekBCandle.getId()).isNotEqualTo(weekAId);
    }
}
