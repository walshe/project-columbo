package walshe.projectcolumbo.api.v1;

import walshe.projectcolumbo.TestDatabaseCleaner;
import walshe.projectcolumbo.TestcontainersConfiguration;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.entity.EmaIndicator;
import walshe.projectcolumbo.persistence.entity.MacdIndicator;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.CandleRepository;
import walshe.projectcolumbo.persistence.repository.EmaRepository;
import walshe.projectcolumbo.persistence.repository.MacdRepository;
import walshe.projectcolumbo.persistence.service.EmaComputationService;
import walshe.projectcolumbo.persistence.service.MacdComputationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class EmaComputationIntegrationTest {

    @Autowired
    private TestDatabaseCleaner testDatabaseCleaner;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private CandleRepository candleRepository;

    @Autowired
    private EmaRepository emaRepository;

    @Autowired
    private MacdRepository macdRepository;

    @Autowired
    private EmaComputationService emaComputationService;

    @Autowired
    private MacdComputationService macdComputationService;

    @BeforeEach
    void setUp() {
        testDatabaseCleaner.cleanAll();
    }

    @Test
    void emaComputationService_persistsRowsForPeriod13() {
        Asset asset = seedAssetWithCandles(50);

        emaComputationService.computeForAsset(asset, Timeframe.D1, 13, false);

        List<EmaIndicator> rows = emaRepository.findByAssetAndTimeframeAndPeriodOrderByCloseTimeAsc(
                asset, Timeframe.D1, 13);
        // 50 candles, period=13: expect 50 - 13 + 1 = 38 rows
        assertThat(rows).hasSize(38);
        assertThat(rows).allMatch(r -> r.getPeriod() == 13);
        assertThat(rows).allMatch(r -> r.getEmaValue() != null);
    }

    @Test
    void emaComputationService_isIdempotent() {
        Asset asset = seedAssetWithCandles(50);

        emaComputationService.computeForAsset(asset, Timeframe.D1, 13, false);
        long countAfterFirst = emaRepository.count();

        emaComputationService.computeForAsset(asset, Timeframe.D1, 13, false);
        long countAfterSecond = emaRepository.count();

        assertThat(countAfterSecond).isEqualTo(countAfterFirst);
    }

    @Test
    void macdComputationService_persistsRows() {
        Asset asset = seedAssetWithCandles(50);

        macdComputationService.computeForAsset(asset, Timeframe.D1, false);

        List<MacdIndicator> rows = macdRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(
                asset, Timeframe.D1);
        // 50 candles, first MACD at index 33: expect 50 - 34 + 1 = 17 rows
        assertThat(rows).hasSize(17);
        assertThat(rows).allMatch(r -> r.getMacdLine() != null);
        assertThat(rows).allMatch(r -> r.getSignalLine() != null);
        assertThat(rows).allMatch(r -> r.getHistogram() != null);
        // histogram ≈ macdLine - signalLine for every row
        // Values are stored at NUMERIC(20,8). The stored histogram is computed from full-precision
        // values before DB rounding, so the retrieved histogram may differ from (storedMacd - storedSignal)
        // by at most 1 ULP at 8 places. Compare at 7 decimal places to tolerate DB rounding drift.
        assertThat(rows).allSatisfy(r ->
                assertThat(r.getHistogram().setScale(7, RoundingMode.HALF_UP))
                        .isEqualByComparingTo(
                                r.getMacdLine().subtract(r.getSignalLine()).setScale(7, RoundingMode.HALF_UP)));
    }

    @Test
    void macdComputationService_isIdempotent() {
        Asset asset = seedAssetWithCandles(50);

        macdComputationService.computeForAsset(asset, Timeframe.D1, false);
        long countAfterFirst = macdRepository.count();

        macdComputationService.computeForAsset(asset, Timeframe.D1, false);
        long countAfterSecond = macdRepository.count();

        assertThat(countAfterSecond).isEqualTo(countAfterFirst);
    }

    @Test
    void emaComputationService_differentPeriodsStoredSeparately() {
        Asset asset = seedAssetWithCandles(50);

        emaComputationService.computeForAsset(asset, Timeframe.D1, 13, false);
        emaComputationService.computeForAsset(asset, Timeframe.D1, 26, false);

        List<EmaIndicator> period13Rows = emaRepository.findByAssetAndTimeframeAndPeriodOrderByCloseTimeAsc(
                asset, Timeframe.D1, 13);
        List<EmaIndicator> period26Rows = emaRepository.findByAssetAndTimeframeAndPeriodOrderByCloseTimeAsc(
                asset, Timeframe.D1, 26);

        assertThat(period13Rows).hasSize(38); // 50 - 13 + 1
        assertThat(period26Rows).hasSize(25); // 50 - 26 + 1
        assertThat(period13Rows.get(0).getEmaValue())
                .isNotEqualByComparingTo(period26Rows.get(0).getEmaValue());
    }

    /**
     * Seeds 50 D1 candles for a BTCUSDT asset with close times in 2024 so they all pass
     * CandleFilters.finalizedBeforeUtcMidnightToday().
     */
    private Asset seedAssetWithCandles(int count) {
        Asset asset = assetRepository.save(new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true));
        OffsetDateTime baseTime = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        for (int i = 0; i < count; i++) {
            Candle c = new Candle();
            c.setAsset(asset);
            c.setTimeframe(Timeframe.D1);
            c.setOpenTime(baseTime.plusDays(i));
            c.setCloseTime(baseTime.plusDays(i));
            c.setOpen(BigDecimal.valueOf(50000 + i * 100L));
            c.setHigh(BigDecimal.valueOf(51000 + i * 100L));
            c.setLow(BigDecimal.valueOf(49000 + i * 100L));
            c.setClose(BigDecimal.valueOf(50000 + i * 100L));
            c.setVolume(BigDecimal.valueOf(1000));
            c.setSource(MarketProvider.BINANCE);
            candleRepository.save(c);
        }
        return asset;
    }
}
