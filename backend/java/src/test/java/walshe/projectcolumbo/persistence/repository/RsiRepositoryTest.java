package walshe.projectcolumbo.persistence.repository;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.RsiIndicator;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.CandleRepository;
import walshe.projectcolumbo.persistence.repository.RsiRepository;
import walshe.projectcolumbo.persistence.repository.SignalStateRepository;
import walshe.projectcolumbo.persistence.repository.SuperTrendRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import walshe.projectcolumbo.TestcontainersConfiguration;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RsiRepositoryTest {

    @Autowired
    private RsiRepository rsiRepository;

    @Autowired
    private SignalStateRepository signalStateRepository;

    @Autowired
    private SuperTrendRepository superTrendRepository;

    @Autowired
    private CandleRepository candleRepository;

    @Autowired
    private AssetRepository assetRepository;

    @BeforeEach
    void setUp() {
        signalStateRepository.deleteAll();
        superTrendRepository.deleteAll();
        rsiRepository.deleteAll();
        candleRepository.deleteAll();
        assetRepository.deleteAll();
    }

    @Test
    void shouldSaveAndFindLatest() {
        // Given
        Asset btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        assetRepository.save(btc);

        OffsetDateTime t1 = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime t2 = t1.plusDays(1);

        RsiIndicator r1 = new RsiIndicator();
        r1.setAsset(btc);
        r1.setTimeframe(Timeframe.D1);
        r1.setCloseTime(t1);
        r1.setRsiValue(new BigDecimal("55.4321"));
        rsiRepository.save(r1);

        RsiIndicator r2 = new RsiIndicator();
        r2.setAsset(btc);
        r2.setTimeframe(Timeframe.D1);
        r2.setCloseTime(t2);
        r2.setRsiValue(new BigDecimal("60.0000"));
        rsiRepository.save(r2);

        // When
        Optional<RsiIndicator> latest = rsiRepository.findFirstByAssetAndTimeframeOrderByCloseTimeDesc(btc, Timeframe.D1);

        // Then
        assertThat(latest).isPresent();
        assertThat(latest.get().getCloseTime()).isEqualTo(t2);
        assertThat(latest.get().getRsiValue()).isEqualByComparingTo("60.0000");
    }

    @Test
    void shouldFindByAssetTimeframeAndCloseTime() {
        // Given
        Asset btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        assetRepository.save(btc);

        OffsetDateTime t1 = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

        RsiIndicator r1 = new RsiIndicator();
        r1.setAsset(btc);
        r1.setTimeframe(Timeframe.D1);
        r1.setCloseTime(t1);
        r1.setRsiValue(new BigDecimal("45.0000"));
        rsiRepository.save(r1);

        // When
        Optional<RsiIndicator> found = rsiRepository.findByAssetAndTimeframeAndCloseTime(btc, Timeframe.D1, t1);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getRsiValue()).isEqualByComparingTo("45.0000");
    }
}
