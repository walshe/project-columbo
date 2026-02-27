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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SignalStateUpdateUnknownTest {

    @Autowired
    private SignalStateService signalStateService;

    @Autowired
    private SignalStateRepository signalStateRepository;

    @Autowired
    private CandleRepository candleRepository;

    @Autowired
    private AssetRepository assetRepository;

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
    void shouldMoveUnknownStateForwardToLatestFinalizedCandle() {
        // Given
        Asset asset = assetRepository.save(new Asset("TEST", "Test Asset", MarketProvider.BINANCE, true));
        OffsetDateTime t1 = OffsetDateTime.of(2026, 2, 20, 23, 59, 59, 0, ZoneOffset.UTC);
        OffsetDateTime t2 = t1.plusDays(1);

        // Day 1: Asset has 5 candles, UNKNOWN state created
        saveCandle(asset, t1);
        signalStateRepository.save(new SignalState(asset, Timeframe.D1, IndicatorType.SUPERTREND, t1, TrendState.UNKNOWN, SignalEvent.NONE));

        // Day 2: Asset has 6 candles, still UNKNOWN. It should update to t2.
        saveCandle(asset, t2);

        // When
        signalStateService.processAsset(asset, Timeframe.D1, false);

        // Then
        Optional<SignalState> latest = signalStateRepository.findFirstByAssetIdAndTimeframeAndIndicatorTypeOrderByCloseTimeDesc(
                asset.getId(), Timeframe.D1, IndicatorType.SUPERTREND
        );

        assertThat(latest).isPresent();
        // The issue is that it should be at t2, but currently it stays at t1
        assertThat(latest.get().getCloseTime()).isEqualTo(t2);
        assertThat(latest.get().getTrendState()).isEqualTo(TrendState.UNKNOWN);
    }

    @Test
    void shouldUpdateUnknownToRealSignalWhenDataAvailable() {
        // Given
        Asset asset = assetRepository.save(new Asset("SUPER", "Super Asset", MarketProvider.BINANCE, true));
        OffsetDateTime t1 = OffsetDateTime.of(2026, 2, 20, 23, 59, 59, 0, ZoneOffset.UTC);
        OffsetDateTime t2 = t1.plusDays(1);

        // Day 1: UNKNOWN state
        saveCandle(asset, t1);
        signalStateRepository.save(new SignalState(asset, Timeframe.D1, IndicatorType.SUPERTREND, t1, TrendState.UNKNOWN, SignalEvent.NONE));

        // Day 2: Now we have a SuperTrend indicator at t2
        saveCandle(asset, t2);
        SuperTrendIndicator indicator = new SuperTrendIndicator();
        indicator.setAsset(asset);
        indicator.setTimeframe(Timeframe.D1);
        indicator.setCloseTime(t2);
        indicator.setAtr(BigDecimal.ONE);
        indicator.setUpperBand(BigDecimal.TEN);
        indicator.setLowerBand(BigDecimal.ONE);
        indicator.setSupertrend(BigDecimal.TEN);
        indicator.setDirection(SuperTrendDirection.DOWN);
        superTrendRepository.save(indicator);

        // When
        signalStateService.processAsset(asset, Timeframe.D1, false);

        // Then
        // We should now have a BEARISH state at t2
        Optional<SignalState> latest = signalStateRepository.findByAssetAndTimeframeAndIndicatorTypeAndCloseTime(
                asset, Timeframe.D1, IndicatorType.SUPERTREND, t2
        );

        assertThat(latest).isPresent();
        assertThat(latest.get().getTrendState()).isEqualTo(TrendState.BEARISH);

        // And the old UNKNOWN state at t1 should still exist (or be deleted/ignored)
        // Actually our logic adds new SignalState for new indicator results.
        Optional<SignalState> old = signalStateRepository.findByAssetAndTimeframeAndIndicatorTypeAndCloseTime(
                asset, Timeframe.D1, IndicatorType.SUPERTREND, t1
        );
        assertThat(old).isPresent();
        assertThat(old.get().getTrendState()).isEqualTo(TrendState.UNKNOWN);
    }

    @Autowired
    private SuperTrendRepository superTrendRepository;

    private void saveCandle(Asset asset, OffsetDateTime closeTime) {
        Candle c = new Candle();
        c.setAsset(asset);
        c.setTimeframe(Timeframe.D1);
        c.setOpenTime(closeTime.minusDays(1).plusSeconds(1));
        c.setCloseTime(closeTime);
        c.setOpen(BigDecimal.ONE);
        c.setHigh(BigDecimal.TEN);
        c.setLow(BigDecimal.ONE);
        c.setClose(BigDecimal.valueOf(5));
        c.setVolume(BigDecimal.ZERO);
        c.setSource(MarketProvider.BINANCE);
        candleRepository.save(c);
    }
}
