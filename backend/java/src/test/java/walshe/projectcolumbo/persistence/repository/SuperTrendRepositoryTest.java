package walshe.projectcolumbo.persistence.repository;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.SuperTrendIndicator;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.SuperTrendDirection;
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
class SuperTrendRepositoryTest {

    @Autowired
    private SuperTrendRepository superTrendRepository;

    @Autowired
    private SignalStateRepository signalStateRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private CandleRepository candleRepository;

    @Autowired
    private RsiRepository rsiRepository;

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

        SuperTrendIndicator st1 = new SuperTrendIndicator();
        st1.setAsset(btc);
        st1.setTimeframe(Timeframe.D1);
        st1.setCloseTime(t1);
        st1.setAtr(new BigDecimal("100.0"));
        st1.setUpperBand(new BigDecimal("41000.0"));
        st1.setLowerBand(new BigDecimal("39000.0"));
        st1.setSupertrend(new BigDecimal("39000.0"));
        st1.setDirection(SuperTrendDirection.UP);
        superTrendRepository.save(st1);

        SuperTrendIndicator st2 = new SuperTrendIndicator();
        st2.setAsset(btc);
        st2.setTimeframe(Timeframe.D1);
        st2.setCloseTime(t2);
        st2.setAtr(new BigDecimal("110.0"));
        st2.setUpperBand(new BigDecimal("42000.0"));
        st2.setLowerBand(new BigDecimal("40000.0"));
        st2.setSupertrend(new BigDecimal("40000.0"));
        st2.setDirection(SuperTrendDirection.UP);
        superTrendRepository.save(st2);

        // When
        Optional<SuperTrendIndicator> latest = superTrendRepository.findFirstByAssetAndTimeframeOrderByCloseTimeDesc(btc, Timeframe.D1);

        // Then
        assertThat(latest).isPresent();
        assertThat(latest.get().getCloseTime()).isEqualTo(t2);
        assertThat(latest.get().getAtr()).isEqualByComparingTo("110.0");
        assertThat(latest.get().getDirection()).isEqualTo(SuperTrendDirection.UP);
    }
}
