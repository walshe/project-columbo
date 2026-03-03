package walshe.projectcolumbo.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import walshe.projectcolumbo.TestcontainersConfiguration;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SignalStateRepositoryTest {

    @Autowired
    private SignalStateRepository signalStateRepository;

    @Autowired
    private SuperTrendRepository superTrendRepository;

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
    void shouldSaveAndFindLatest() {
        // Given
        Asset btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        assetRepository.save(btc);

        OffsetDateTime t1 = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime t2 = t1.plusDays(1);

        SignalState s1 = new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, t1, TrendState.BULLISH, SignalEvent.NONE);
        signalStateRepository.save(s1);

        SignalState s2 = new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, t2, TrendState.BEARISH, SignalEvent.BEARISH_REVERSAL);
        signalStateRepository.save(s2);

        // When
        Optional<SignalState> latest = signalStateRepository.findFirstByAssetIdAndTimeframeAndIndicatorTypeOrderByCloseTimeDesc(
                btc.getId(), Timeframe.D1, IndicatorType.SUPERTREND
        );

        // Then
        assertThat(latest).isPresent();
        assertThat(latest.get().getCloseTime()).isEqualTo(t2);
        assertThat(latest.get().getTrendState()).isEqualTo(TrendState.BEARISH);
        assertThat(latest.get().getEvent()).isEqualTo(SignalEvent.BEARISH_REVERSAL);
    }

    @Test
    void shouldFindAllOrdered() {
        // Given
        Asset eth = new Asset("ETHUSDT", "Ethereum", MarketProvider.BINANCE, true);
        assetRepository.save(eth);

        OffsetDateTime t1 = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime t2 = t1.plusDays(1);
        OffsetDateTime t3 = t1.plusDays(2);

        signalStateRepository.save(new SignalState(eth, Timeframe.D1, IndicatorType.SUPERTREND, t2, TrendState.BULLISH, SignalEvent.NONE));
        signalStateRepository.save(new SignalState(eth, Timeframe.D1, IndicatorType.SUPERTREND, t1, TrendState.BULLISH, SignalEvent.NONE));
        signalStateRepository.save(new SignalState(eth, Timeframe.D1, IndicatorType.SUPERTREND, t3, TrendState.BEARISH, SignalEvent.BEARISH_REVERSAL));

        // When
        List<SignalState> states = signalStateRepository.findAllByAssetIdAndTimeframeAndIndicatorTypeOrderByCloseTimeAsc(
                eth.getId(), Timeframe.D1, IndicatorType.SUPERTREND
        );

        // Then
        assertThat(states).hasSize(3);
        assertThat(states.get(0).getCloseTime()).isEqualTo(t1);
        assertThat(states.get(1).getCloseTime()).isEqualTo(t2);
        assertThat(states.get(2).getCloseTime()).isEqualTo(t3);
    }

    @Test
    void shouldFindLatestForActiveAssets() {
        // Given
        Asset btc = assetRepository.save(new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true));
        Asset eth = assetRepository.save(new Asset("ETHUSDT", "Ethereum", MarketProvider.BINANCE, true));
        Asset inactive = assetRepository.save(new Asset("XRPUSDT", "Ripple", MarketProvider.BINANCE, false));

        OffsetDateTime t1 = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime t2 = t1.plusDays(1);

        // BTC: latest is t2
        signalStateRepository.save(new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, t1, TrendState.BULLISH, SignalEvent.NONE));
        signalStateRepository.save(new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, t2, TrendState.BEARISH, SignalEvent.BEARISH_REVERSAL));

        // ETH: latest is t1
        signalStateRepository.save(new SignalState(eth, Timeframe.D1, IndicatorType.SUPERTREND, t1, TrendState.BULLISH, SignalEvent.NONE));

        // Inactive: should be ignored
        signalStateRepository.save(new SignalState(inactive, Timeframe.D1, IndicatorType.SUPERTREND, t2, TrendState.BULLISH, SignalEvent.NONE));

        // When
        OffsetDateTime boundary = t2.plusDays(1);
        List<SignalState> latest = signalStateRepository.findLatestFinalizedForActiveAssets(Timeframe.D1, IndicatorType.SUPERTREND, boundary);

        // Then
        assertThat(latest).hasSize(2);
        assertThat(latest).anySatisfy(s -> {
            assertThat(s.getAsset().getSymbol()).isEqualTo("BTCUSDT");
            assertThat(s.getCloseTime()).isEqualTo(t2);
        });
        assertThat(latest).anySatisfy(s -> {
            assertThat(s.getAsset().getSymbol()).isEqualTo("ETHUSDT");
            assertThat(s.getCloseTime()).isEqualTo(t1);
        });
    }

    @Test
    void shouldFindLatestIncludingUnknownForActiveAssets() {
        // Given
        Asset btc = assetRepository.save(new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true));
        Asset eth = assetRepository.save(new Asset("ETHUSDT", "Ethereum", MarketProvider.BINANCE, true));

        OffsetDateTime t1 = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

        signalStateRepository.save(new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, t1, TrendState.BULLISH, SignalEvent.NONE));
        signalStateRepository.save(new SignalState(eth, Timeframe.D1, IndicatorType.SUPERTREND, t1, TrendState.UNKNOWN, SignalEvent.NONE));

        // When
        OffsetDateTime boundary = t1.plusDays(1);
        List<SignalState> latest = signalStateRepository.findLatestFinalizedForActiveAssets(Timeframe.D1, IndicatorType.SUPERTREND, boundary);

        // Then
        assertThat(latest).hasSize(2);
        assertThat(latest).anySatisfy(s -> {
            assertThat(s.getAsset().getSymbol()).isEqualTo("BTCUSDT");
            assertThat(s.getTrendState()).isEqualTo(TrendState.BULLISH);
        });
        assertThat(latest).anySatisfy(s -> {
            assertThat(s.getAsset().getSymbol()).isEqualTo("ETHUSDT");
            assertThat(s.getTrendState()).isEqualTo(TrendState.UNKNOWN);
        });
    }

    @Test
    void shouldFindLatestFlipsForActiveAssets() {
        // Given
        Asset btc = assetRepository.save(new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true));
        Asset eth = assetRepository.save(new Asset("ETHUSDT", "Ethereum", MarketProvider.BINANCE, true));

        OffsetDateTime t1 = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime t2 = t1.plusDays(1);
        OffsetDateTime t3 = t1.plusDays(2);

        // BTC: flips at t1 and t3. Latest flip is t3.
        signalStateRepository.save(new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, t1, TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL));
        signalStateRepository.save(new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, t2, TrendState.BULLISH, SignalEvent.NONE));
        signalStateRepository.save(new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, t3, TrendState.BEARISH, SignalEvent.BEARISH_REVERSAL));

        // ETH: flip at t2.
        signalStateRepository.save(new SignalState(eth, Timeframe.D1, IndicatorType.SUPERTREND, t1, TrendState.BULLISH, SignalEvent.NONE));
        signalStateRepository.save(new SignalState(eth, Timeframe.D1, IndicatorType.SUPERTREND, t2, TrendState.BEARISH, SignalEvent.BEARISH_REVERSAL));

        // When
        OffsetDateTime boundary = t3.plusDays(1);
        List<SignalState> flips = signalStateRepository.findLatestFinalizedFlipsForActiveAssets(Timeframe.D1, IndicatorType.SUPERTREND, boundary);

        // Then
        assertThat(flips).hasSize(2);
        assertThat(flips).anySatisfy(s -> {
            assertThat(s.getAsset().getSymbol()).isEqualTo("BTCUSDT");
            assertThat(s.getCloseTime()).isEqualTo(t3);
        });
        assertThat(flips).anySatisfy(s -> {
            assertThat(s.getAsset().getSymbol()).isEqualTo("ETHUSDT");
            assertThat(s.getCloseTime()).isEqualTo(t2);
        });
    }

    @Test
    void findEventMatches_ShouldReturnCorrectAssets() {
        // Given
        Asset btc = assetRepository.save(new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true));
        Asset eth = assetRepository.save(new Asset("ETHUSDT", "Ethereum", MarketProvider.BINANCE, true));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        signalStateRepository.save(new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, now, TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL));
        signalStateRepository.save(new SignalState(eth, Timeframe.D1, IndicatorType.SUPERTREND, now, TrendState.BEARISH, SignalEvent.NONE));

        // When
        List<SignalState> matches = signalStateRepository.findEventMatches(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, Timeframe.D1, now, null);

        // Then
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getAsset().getSymbol()).isEqualTo("BTCUSDT");
    }

    @Test
    void findEventMatches_WithMaxDaysSinceCross_ShouldFilterCorrectly() {
        // Given
        Asset btc = assetRepository.save(new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true));
        Asset eth = assetRepository.save(new Asset("ETHUSDT", "Ethereum", MarketProvider.BINANCE, true));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.DAYS);

        // BTC: CROSSED_ABOVE_60 today
        signalStateRepository.save(new SignalState(btc, Timeframe.D1, IndicatorType.RSI, now, TrendState.ABOVE_60, SignalEvent.CROSSED_ABOVE_60));
        
        // ETH: CROSSED_ABOVE_60 10 days ago
        signalStateRepository.save(new SignalState(eth, Timeframe.D1, IndicatorType.RSI, now.minusDays(10), TrendState.ABOVE_60, SignalEvent.CROSSED_ABOVE_60));

        // When
        List<SignalState> matches = signalStateRepository.findEventMatches(IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, Timeframe.D1, now, 5);

        // Then
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getAsset().getSymbol()).isEqualTo("BTCUSDT");
    }

    @Test
    void findStateMatches_ShouldReturnCorrectAssets() {
        // Given
        Asset btc = assetRepository.save(new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true));
        Asset eth = assetRepository.save(new Asset("ETHUSDT", "Ethereum", MarketProvider.BINANCE, true));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        signalStateRepository.save(new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, now, TrendState.BULLISH, SignalEvent.NONE));
        signalStateRepository.save(new SignalState(eth, Timeframe.D1, IndicatorType.SUPERTREND, now, TrendState.BEARISH, SignalEvent.NONE));

        // When
        List<SignalState> matches = signalStateRepository.findStateMatches(IndicatorType.SUPERTREND, TrendState.BULLISH, Timeframe.D1, null);

        // Then
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getAsset().getSymbol()).isEqualTo("BTCUSDT");
    }

    @Test
    void findStateMatches_WithMaxDaysSinceFlip_ShouldFilterCorrectly() {
        // Given
        Asset btc = assetRepository.save(new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true));
        Asset eth = assetRepository.save(new Asset("ETHUSDT", "Ethereum", MarketProvider.BINANCE, true));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime old = now.minusDays(10);

        // BTC: latest is BULLISH, flipped 2 days ago (actually it's the latest state, the flip happened 2 days ago)
        // For simplicity, let's say the LATEST state's closeTime is what we check against maxDaysSinceFlip
        // Wait, the requirement says "latest trend_state per asset (optionally with recency filter)".
        // My implementation checks s.closeTime >= :flipBoundary where s is the LATEST state.
        
        signalStateRepository.save(new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, now.minusDays(2), TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL));
        signalStateRepository.save(new SignalState(eth, Timeframe.D1, IndicatorType.SUPERTREND, old, TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL));

        // When
        List<SignalState> matches = signalStateRepository.findStateMatches(IndicatorType.SUPERTREND, TrendState.BULLISH, Timeframe.D1, 5);

        // Then
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getAsset().getSymbol()).isEqualTo("BTCUSDT");
    }
}
