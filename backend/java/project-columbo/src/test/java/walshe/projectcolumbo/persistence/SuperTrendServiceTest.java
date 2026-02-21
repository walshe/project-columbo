package walshe.projectcolumbo.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import walshe.projectcolumbo.TestcontainersConfiguration;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SuperTrendServiceTest {

    @Autowired
    private SuperTrendService superTrendService;

    @Autowired
    private SuperTrendRepository superTrendRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private CandleRepository candleRepository;

    @Autowired
    private SignalStateRepository signalStateRepository;

    @Autowired
    private EntityManager entityManager;

    private Asset btc;

    @BeforeEach
    void setUp() {
        signalStateRepository.deleteAll();
        superTrendRepository.deleteAll();
        candleRepository.deleteAll();
        assetRepository.deleteAll();

        btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        assetRepository.save(btc);
    }

    @Test
    void shouldProcessAssetAndPersistResults() {
        // Given: 15 candles (enough for ATR 10)
        // We need ATR + some candles for TR. SuperTrend usually needs ATR_LENGTH + 1 candles for first ATR.
        // Actually, SuperTrendCalculator.calculateATR needs n items from TR.
        // So 11 candles -> 10 TRs -> 1 ATR.
        
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
        for (int i = 0; i < 20; i++) {
            saveCandle(baseTime.plusDays(i), new BigDecimal("10000"), new BigDecimal("10500"), new BigDecimal("9500"), new BigDecimal("10200"));
        }

        // When
        superTrendService.processAsset(btc, Timeframe.D1, 10, new BigDecimal("3.0"), false);

        // Then
        List<SuperTrendIndicator> stored = superTrendRepository.findAll();
        // 20 candles -> 19 TRs (index 1..19) -> 10th ATR at index 10 (11th candle)
        // so candles index 10 to 19 have results. 19 - 10 + 1 = 10 results.
        assertThat(stored).isNotEmpty();
        
        int expectedCount = 11;
        assertThat(stored).hasSize(expectedCount);
    }

    @Test
    void shouldBeIdempotent() {
        // Given
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
        for (int i = 0; i < 20; i++) {
            saveCandle(baseTime.plusDays(i), new BigDecimal("10000"), new BigDecimal("10500"), new BigDecimal("9500"), new BigDecimal("10200"));
        }

        // When
        superTrendService.processAsset(btc, Timeframe.D1, 10, new BigDecimal("3.0"), false);
        long countFirstRun = superTrendRepository.count();

        superTrendService.processAsset(btc, Timeframe.D1, 10, new BigDecimal("3.0"), false);
        long countSecondRun = superTrendRepository.count();

        // Then
        assertThat(countFirstRun).isEqualTo(countSecondRun);
    }

    @Test
    @Transactional
    void shouldUpdateOnRevision() {
        // Given
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
        for (int i = 0; i < 20; i++) {
            saveCandle(baseTime.plusDays(i), new BigDecimal("10000"), new BigDecimal("10500"), new BigDecimal("9500"), new BigDecimal("10200"));
        }

        superTrendService.processAsset(btc, Timeframe.D1, 10, new BigDecimal("3.0"), false);
        entityManager.flush();
        entityManager.clear();
        
        // Manually modify one stored value to simulate revision
        List<SuperTrendIndicator> stored = superTrendRepository.findAll();
        SuperTrendIndicator first = stored.get(0);
        BigDecimal originalAtr = first.getAtr();
        BigDecimal modifiedAtr = originalAtr.add(BigDecimal.ONE);
        first.setAtr(modifiedAtr);
        superTrendRepository.saveAndFlush(first);
        entityManager.clear();

        // When
        superTrendService.processAsset(btc, Timeframe.D1, 10, new BigDecimal("3.0"), true); // Use fullRecalc to ensure we hit the modified record
        entityManager.flush();
        entityManager.clear();

        // Then
        SuperTrendIndicator updated = superTrendRepository.findById(first.getId()).orElseThrow();
        assertThat(updated.getAtr()).isEqualByComparingTo(originalAtr); // Should be reverted to calculator's value
    }

    private void saveCandle(OffsetDateTime closeTime, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
        Candle candle = new Candle();
        candle.setAsset(btc);
        candle.setTimeframe(Timeframe.D1);
        candle.setOpenTime(closeTime.minusDays(1));
        candle.setCloseTime(closeTime);
        candle.setOpen(open);
        candle.setHigh(high);
        candle.setLow(low);
        candle.setClose(close);
        candle.setVolume(BigDecimal.ZERO);
        candle.setSource(MarketProvider.BINANCE);
        candleRepository.save(candle);
    }
}
