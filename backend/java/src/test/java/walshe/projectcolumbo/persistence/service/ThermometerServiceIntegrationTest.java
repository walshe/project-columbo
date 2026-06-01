package walshe.projectcolumbo.persistence.service;

import walshe.projectcolumbo.TestDatabaseCleaner;
import walshe.projectcolumbo.TestcontainersConfiguration;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.entity.ThermometerIndicator;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.CandleRepository;
import walshe.projectcolumbo.persistence.repository.ThermometerRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ThermometerServiceIntegrationTest {

    @Autowired
    private TestDatabaseCleaner testDatabaseCleaner;

    @Autowired
    private ThermometerService thermometerService;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private CandleRepository candleRepository;

    @Autowired
    private ThermometerRepository thermometerRepository;

    @BeforeEach
    void setUp() {
        testDatabaseCleaner.cleanAll();
    }

    @AfterEach
    void tearDown() {
        thermometerRepository.deleteAll();
        candleRepository.deleteAll();
        assetRepository.deleteAll();
    }

    @Test
    void shouldPersist24Rows_given25D1Candles() {
        Asset asset = seedAsset("BTCUSDT");
        seedD1Candles(asset, 25);

        thermometerService.computeForAsset(asset, false);

        List<ThermometerIndicator> rows =
                thermometerRepository.findByAssetOrderByCloseTimeAsc(asset);
        // 25 candles → 24 temperature values (starting at candles[1])
        assertThat(rows).hasSize(24);
    }

    @Test
    void shouldHaveNullEma_forFirst21Rows() {
        Asset asset = seedAsset("BTCUSDT");
        seedD1Candles(asset, 25);

        thermometerService.computeForAsset(asset, false);

        List<ThermometerIndicator> rows =
                thermometerRepository.findByAssetOrderByCloseTimeAsc(asset);
        assertThat(rows).hasSize(24);
        // First 21 temperature values → EMA period=22, insufficient history → null
        for (int i = 0; i < 21; i++) {
            assertThat(rows.get(i).getTemperatureEma())
                    .as("Expected null ema at row index %d", i)
                    .isNull();
        }
    }

    @Test
    void shouldHaveNonNullEma_forLast3Rows() {
        Asset asset = seedAsset("BTCUSDT");
        seedD1Candles(asset, 25);

        thermometerService.computeForAsset(asset, false);

        List<ThermometerIndicator> rows =
                thermometerRepository.findByAssetOrderByCloseTimeAsc(asset);
        assertThat(rows).hasSize(24);
        // With 24 temperature values and period=22: 3 EMA values at indices 21, 22, 23
        assertThat(rows.get(21).getTemperatureEma()).isNotNull();
        assertThat(rows.get(22).getTemperatureEma()).isNotNull();
        assertThat(rows.get(23).getTemperatureEma()).isNotNull();
    }

    @Test
    void shouldSkip_whenFewerThan2Candles() {
        Asset asset = seedAsset("BTCUSDT");
        seedD1Candles(asset, 1);

        thermometerService.computeForAsset(asset, false);

        assertThat(thermometerRepository.count()).isEqualTo(0);
    }

    @Test
    void shouldBeIdempotent() {
        Asset asset = seedAsset("BTCUSDT");
        seedD1Candles(asset, 25);

        thermometerService.computeForAsset(asset, false);
        long countAfterFirst = thermometerRepository.count();

        thermometerService.computeForAsset(asset, false);
        long countAfterSecond = thermometerRepository.count();

        assertThat(countAfterSecond).isEqualTo(countAfterFirst);
    }

    @Test
    void shouldHavePositiveTemperature_givenAscendingHighs() {
        Asset asset = seedAsset("BTCUSDT");
        seedD1Candles(asset, 5);

        thermometerService.computeForAsset(asset, false);

        List<ThermometerIndicator> rows =
                thermometerRepository.findByAssetOrderByCloseTimeAsc(asset);
        assertThat(rows).isNotEmpty();
        assertThat(rows).allMatch(r -> r.getTemperature().compareTo(BigDecimal.ZERO) >= 0);
    }

    // ---- helpers ----

    private Asset seedAsset(String symbol) {
        return assetRepository.save(new Asset(symbol, symbol, MarketProvider.BINANCE, true));
    }

    /**
     * Seeds n D1 candles with ascending highs (so temperature > 0 for most bars).
     * CloseTimes start at 2024-01-01 and are spaced 1 day apart.
     */
    private void seedD1Candles(Asset asset, int n) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Candle c = new Candle();
            c.setAsset(asset);
            c.setTimeframe(Timeframe.D1);
            OffsetDateTime time = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).plusDays(i);
            c.setOpenTime(time);
            c.setCloseTime(time);
            c.setOpen(BigDecimal.valueOf(50000));
            c.setHigh(BigDecimal.valueOf(50000 + i * 200L));    // ascending high
            c.setLow(BigDecimal.valueOf(49000 + i * 100L));     // ascending low
            c.setClose(BigDecimal.valueOf(50000 + i * 150L));
            c.setVolume(BigDecimal.valueOf(1000));
            c.setSource(MarketProvider.BINANCE);
            candles.add(c);
        }
        candleRepository.saveAll(candles);
    }
}
