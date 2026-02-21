package walshe.projectcolumbo.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import walshe.projectcolumbo.TestcontainersConfiguration;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SignalStateServiceLogicTest {

    @Autowired
    private SignalStateService signalStateService;

    @Autowired
    private SignalStateRepository signalStateRepository;

    @Autowired
    private SuperTrendRepository superTrendRepository;

    @Autowired
    private AssetRepository assetRepository;

    private Asset btc;

    @BeforeEach
    void setUp() {
        signalStateRepository.deleteAll();
        superTrendRepository.deleteAll();
        assetRepository.deleteAll();

        btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        assetRepository.save(btc);
    }

    @Test
    void shouldFetchFinalizedSuperTrendRows() {
        // Given: UTC midnight boundary
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime boundary = now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);

        // Saved rows: one before boundary (finalized), one at boundary (not finalized)
        saveSuperTrend(boundary.minusHours(1), SuperTrendDirection.UP);
        saveSuperTrend(boundary, SuperTrendDirection.UP);

        // When
        signalStateService.processAsset(btc, Timeframe.D1, false);

        // Then (Verification is via logging for now, but we can check if it runs without error)
        // In next phases we will check persistence.
    }

    private void saveSuperTrend(OffsetDateTime closeTime, SuperTrendDirection direction) {
        SuperTrendIndicator indicator = new SuperTrendIndicator();
        indicator.setAsset(btc);
        indicator.setTimeframe(Timeframe.D1);
        indicator.setCloseTime(closeTime);
        indicator.setAtr(BigDecimal.ONE);
        indicator.setUpperBand(BigDecimal.TEN);
        indicator.setLowerBand(BigDecimal.ONE);
        indicator.setSupertrend(BigDecimal.ONE);
        indicator.setDirection(direction);
        superTrendRepository.save(indicator);
    }
}
