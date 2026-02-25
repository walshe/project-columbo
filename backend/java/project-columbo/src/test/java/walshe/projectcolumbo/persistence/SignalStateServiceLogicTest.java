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

    @Autowired
    private CandleRepository candleRepository;

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
    void shouldPersistSignalStates() {
        // Given: UTC midnight boundary
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime boundary = now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);

        // Saved rows: two before boundary (finalized)
        OffsetDateTime t1 = boundary.minusDays(2);
        OffsetDateTime t2 = boundary.minusDays(1);
        saveSuperTrend(t1, SuperTrendDirection.DOWN);
        saveSuperTrend(t2, SuperTrendDirection.UP);

        // When
        signalStateService.processAsset(btc, Timeframe.D1, false);

        // Then
        List<SignalState> states = signalStateRepository.findAllByAssetIdAndTimeframeAndIndicatorTypeOrderByCloseTimeAsc(
                btc.getId(), Timeframe.D1, IndicatorType.SUPERTREND);

        assertThat(states).hasSize(2);
        assertThat(states.get(0).getCloseTime()).isEqualTo(t1);
        assertThat(states.get(0).getTrendState()).isEqualTo(TrendState.BEARISH);
        assertThat(states.get(0).getEvent()).isEqualTo(SignalEvent.NONE);

        assertThat(states.get(1).getCloseTime()).isEqualTo(t2);
        assertThat(states.get(1).getTrendState()).isEqualTo(TrendState.BULLISH);
        assertThat(states.get(1).getEvent()).isEqualTo(SignalEvent.BULLISH_REVERSAL);
    }

    @Test
    void shouldHandleUpsertAndRevision() {
        // Given
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime boundary = now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime t1 = boundary.minusDays(1);
        
        saveSuperTrend(t1, SuperTrendDirection.DOWN);
        
        // Initial run
        signalStateService.processAsset(btc, Timeframe.D1, false);
        
        List<SignalState> statesBefore = signalStateRepository.findAllByAssetIdAndTimeframeAndIndicatorTypeOrderByCloseTimeAsc(
                btc.getId(), Timeframe.D1, IndicatorType.SUPERTREND);
        assertThat(statesBefore).hasSize(1);
        assertThat(statesBefore.get(0).getTrendState()).isEqualTo(TrendState.BEARISH);

        // Change the direction in SuperTrend (simulate a data correction)
        SuperTrendIndicator indicator = superTrendRepository.findByAssetAndTimeframeAndCloseTime(btc, Timeframe.D1, t1).orElseThrow();
        indicator.setDirection(SuperTrendDirection.UP);
        superTrendRepository.save(indicator);

        // When - run again with fullRecalc to pick up the change
        signalStateService.processAsset(btc, Timeframe.D1, true);

        // Then
        List<SignalState> statesAfter = signalStateRepository.findAllByAssetIdAndTimeframeAndIndicatorTypeOrderByCloseTimeAsc(
                btc.getId(), Timeframe.D1, IndicatorType.SUPERTREND);
        assertThat(statesAfter).hasSize(1);
        assertThat(statesAfter.get(0).getTrendState()).isEqualTo(TrendState.BULLISH);
        // ID should be the same (updated, not re-inserted)
        assertThat(statesAfter.get(0).getId()).isEqualTo(statesBefore.get(0).getId());
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
