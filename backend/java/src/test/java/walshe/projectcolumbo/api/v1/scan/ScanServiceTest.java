package walshe.projectcolumbo.api.v1.scan;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.SignalState;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.SignalEvent;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.TrendState;
import walshe.projectcolumbo.persistence.repository.AssetLiquidityRepository;
import walshe.projectcolumbo.persistence.repository.CandleRepository;
import walshe.projectcolumbo.persistence.repository.RsiRepository;
import walshe.projectcolumbo.persistence.repository.SignalStateRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import walshe.projectcolumbo.api.v1.scan.dto.*;


import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    @Mock
    private SignalStateRepository signalStateRepository;
    @Mock
    private CandleRepository candleRepository;
    @Mock
    private ScanValidator scanValidator;
    @Mock
    private RsiRepository rsiRepository;
    @Mock
    private AssetLiquidityRepository assetLiquidityRepository;

    private ScanService scanService;

    @BeforeEach
    void setUp() {
        scanService = new ScanService(signalStateRepository, candleRepository, scanValidator, rsiRepository, assetLiquidityRepository);
        when(assetLiquidityRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void execute_SingleCondition_ReturnsCorrectResults() {
        Asset asset1 = new Asset(); asset1.setId(1L); asset1.setSymbol("BTCUSDT");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        SignalState s1 = createSignal(asset1, IndicatorType.SUPERTREND, SignalEvent.SUPERTREND_BULLISH_REVERSAL, now);

        when(candleRepository.findLatestCloseTimeForTimeframe("D1")).thenReturn(Optional.of(now));
        when(signalStateRepository.findEventMatches(IndicatorType.SUPERTREND, SignalEvent.SUPERTREND_BULLISH_REVERSAL, Timeframe.D1, now, null))
                .thenReturn(List.of(s1));

        ScanRequest request = new ScanRequest(
                ScanOperator.AND,
                List.of(new ScanCondition(Timeframe.D1, IndicatorType.SUPERTREND, SignalEvent.SUPERTREND_BULLISH_REVERSAL, null, null, null)),
                null
        );

        ScanResponse response = scanService.execute(request);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).assetSymbol()).isEqualTo("BTCUSDT");
        assertThat(response.results().get(0).matchedIndicators()).hasSize(1);
        MatchedIndicator mi = response.results().get(0).matchedIndicators().get(0);
        assertThat(mi).isInstanceOf(SupertrendMatch.class);
        SupertrendMatch sm = (SupertrendMatch) mi;
        assertThat(sm.event()).isEqualTo(SignalEvent.SUPERTREND_BULLISH_REVERSAL);
        verify(scanValidator).validate(request);
    }

    @Test
    void execute_TwoConditionsAND_ReturnsIntersection() {
        Asset asset1 = new Asset(); asset1.setId(1L); asset1.setSymbol("BTCUSDT");
        Asset asset2 = new Asset(); asset2.setId(2L); asset2.setSymbol("ETHUSDT");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        SignalState s1 = createSignal(asset1, IndicatorType.SUPERTREND, SignalEvent.SUPERTREND_BULLISH_REVERSAL, now);
        SignalState s2 = createSignal(asset2, IndicatorType.SUPERTREND, SignalEvent.SUPERTREND_BULLISH_REVERSAL, now);
        SignalState s3 = createSignal(asset1, IndicatorType.RSI, SignalEvent.RSI_CROSSED_ABOVE_60, now);

        when(candleRepository.findLatestCloseTimeForTimeframe("D1")).thenReturn(Optional.of(now));
        when(signalStateRepository.findEventMatches(IndicatorType.SUPERTREND, SignalEvent.SUPERTREND_BULLISH_REVERSAL, Timeframe.D1, now, null))
                .thenReturn(List.of(s1, s2));
        when(signalStateRepository.findEventMatches(IndicatorType.RSI, SignalEvent.RSI_CROSSED_ABOVE_60, Timeframe.D1, now, null))
                .thenReturn(List.of(s3));

        ScanRequest request = new ScanRequest(
                ScanOperator.AND,
                List.of(
                        new ScanCondition(Timeframe.D1, IndicatorType.SUPERTREND, SignalEvent.SUPERTREND_BULLISH_REVERSAL, null, null, null),
                        new ScanCondition(Timeframe.D1, IndicatorType.RSI, SignalEvent.RSI_CROSSED_ABOVE_60, null, null, null)
                ),
                null
        );

        ScanResponse response = scanService.execute(request);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).assetSymbol()).isEqualTo("BTCUSDT");
        assertThat(response.results().get(0).matchedIndicators()).hasSize(2);

        // Verify internal indicator order: RSI before SUPERTREND
        assertThat(response.results().get(0).matchedIndicators().get(0).indicatorType()).isEqualTo(IndicatorType.RSI);
        assertThat(response.results().get(0).matchedIndicators().get(1).indicatorType()).isEqualTo(IndicatorType.SUPERTREND);
    }

    @Test
    void execute_TwoConditionsOR_ReturnsUnion() {
        Asset asset1 = new Asset(); asset1.setId(1L); asset1.setSymbol("BTCUSDT");
        Asset asset2 = new Asset(); asset2.setId(2L); asset2.setSymbol("ETHUSDT");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        SignalState s1 = createSignal(asset1, IndicatorType.SUPERTREND, SignalEvent.SUPERTREND_BULLISH_REVERSAL, now);
        SignalState s2 = createSignal(asset2, IndicatorType.RSI, SignalEvent.RSI_CROSSED_ABOVE_60, now);

        when(candleRepository.findLatestCloseTimeForTimeframe("D1")).thenReturn(Optional.of(now));
        when(signalStateRepository.findEventMatches(IndicatorType.SUPERTREND, SignalEvent.SUPERTREND_BULLISH_REVERSAL, Timeframe.D1, now, null))
                .thenReturn(List.of(s1));
        when(signalStateRepository.findEventMatches(IndicatorType.RSI, SignalEvent.RSI_CROSSED_ABOVE_60, Timeframe.D1, now, null))
                .thenReturn(List.of(s2));

        ScanRequest request = new ScanRequest(
                ScanOperator.OR,
                List.of(
                        new ScanCondition(Timeframe.D1, IndicatorType.SUPERTREND, SignalEvent.SUPERTREND_BULLISH_REVERSAL, null, null, null),
                        new ScanCondition(Timeframe.D1, IndicatorType.RSI, SignalEvent.RSI_CROSSED_ABOVE_60, null, null, null)
                ),
                null
        );

        ScanResponse response = scanService.execute(request);

        assertThat(response.results()).hasSize(2);
        assertThat(response.results()).extracting(ScanResult::assetSymbol).containsExactlyInAnyOrder("BTCUSDT", "ETHUSDT");
    }

    @Test
    void execute_NoMatches_ReturnsEmptyList() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        when(candleRepository.findLatestCloseTimeForTimeframe("D1")).thenReturn(Optional.of(now));
        when(signalStateRepository.findEventMatches(IndicatorType.SUPERTREND, SignalEvent.SUPERTREND_BULLISH_REVERSAL, Timeframe.D1, now, null))
                .thenReturn(List.of());

        ScanRequest request = new ScanRequest(
                ScanOperator.AND,
                List.of(new ScanCondition(Timeframe.D1, IndicatorType.SUPERTREND, SignalEvent.SUPERTREND_BULLISH_REVERSAL, null, null, null)),
                null
        );

        ScanResponse response = scanService.execute(request);

        assertThat(response.results()).isEmpty();
    }

    @Test
    void execute_StateCondition_ReturnsResults() {
        Asset asset1 = new Asset(); asset1.setId(1L); asset1.setSymbol("BTCUSDT");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        SignalState s1 = createSignal(asset1, IndicatorType.SUPERTREND, SignalEvent.NONE, now.minusDays(2));
        s1.setTrendState(TrendState.SUPERTREND_BULLISH);

        when(candleRepository.findLatestCloseTimeForTimeframe("D1")).thenReturn(Optional.of(now));
        when(signalStateRepository.findStateMatches(IndicatorType.SUPERTREND, TrendState.SUPERTREND_BULLISH, Timeframe.D1, 5))
                .thenReturn(List.of(s1));

        // Mocking flip time search
        when(signalStateRepository.findLastDifferentStateTime(eq(1L), eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BULLISH), any()))
                .thenReturn(Optional.of(now.minusDays(3)));
        when(signalStateRepository.findFirstCurrentStateTimeAfter(eq(1L), eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BULLISH), any(), any()))
                .thenReturn(Optional.of(now.minusDays(2)));

        ScanRequest request = new ScanRequest(
                ScanOperator.AND,
                List.of(new ScanCondition(Timeframe.D1, IndicatorType.SUPERTREND, null, TrendState.SUPERTREND_BULLISH, 5, null)),
                null
        );

        ScanResponse response = scanService.execute(request);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).assetSymbol()).isEqualTo("BTCUSDT");
        MatchedIndicator mi = response.results().get(0).matchedIndicators().get(0);
        assertThat(mi).isInstanceOf(SupertrendMatch.class);
        SupertrendMatch sm = (SupertrendMatch) mi;
        assertThat(sm.state()).isEqualTo(TrendState.SUPERTREND_BULLISH);
        assertThat(sm.daysSinceFlip()).isEqualTo(2);
    }

    @Test
    void execute_MultipleResults_OrdersCorrectly() {
        Asset asset1 = new Asset(); asset1.setId(1L); asset1.setSymbol("BTCUSDT");
        Asset asset2 = new Asset(); asset2.setId(2L); asset2.setSymbol("ETHUSDT");
        Asset asset3 = new Asset(); asset3.setId(3L); asset3.setSymbol("SOLUSDT");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // BTC: RSI cross 2 days ago, Supertrend flip 5 days ago
        SignalState s1 = createSignal(asset1, IndicatorType.RSI, SignalEvent.RSI_CROSSED_ABOVE_60, now.minusDays(2));
        SignalState s2 = createSignal(asset1, IndicatorType.SUPERTREND, SignalEvent.NONE, now.minusDays(5));
        s2.setTrendState(TrendState.SUPERTREND_BULLISH);

        // ETH: RSI cross 1 day ago, Supertrend flip 10 days ago
        SignalState s3 = createSignal(asset2, IndicatorType.RSI, SignalEvent.RSI_CROSSED_ABOVE_60, now.minusDays(1));
        SignalState s4 = createSignal(asset2, IndicatorType.SUPERTREND, SignalEvent.NONE, now.minusDays(10));
        s4.setTrendState(TrendState.SUPERTREND_BULLISH);

        // SOL: No RSI match, Supertrend flip 1 day ago
        SignalState s5 = createSignal(asset3, IndicatorType.SUPERTREND, SignalEvent.NONE, now.minusDays(1));
        s5.setTrendState(TrendState.SUPERTREND_BULLISH);

        when(candleRepository.findLatestCloseTimeForTimeframe("D1")).thenReturn(Optional.of(now));

        // Mocking signal search - using OR to get all
        when(signalStateRepository.findEventMatches(eq(IndicatorType.RSI), any(), any(), any(), any()))
                .thenReturn(List.of(s1, s3));
        when(signalStateRepository.findStateMatches(eq(IndicatorType.SUPERTREND), any(), any(), any()))
                .thenReturn(List.of(s2, s4, s5));

        // Mocking flip time searches
        when(signalStateRepository.findLastDifferentStateTime(any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty()); // Simple case
        when(signalStateRepository.findFirstCurrentStateTimeAfter(eq(1L), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(now.minusDays(5)));
        when(signalStateRepository.findFirstCurrentStateTimeAfter(eq(2L), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(now.minusDays(10)));
        when(signalStateRepository.findFirstCurrentStateTimeAfter(eq(3L), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(now.minusDays(1)));

        ScanRequest request = new ScanRequest(
                ScanOperator.OR,
                List.of(
                        new ScanCondition(Timeframe.D1, IndicatorType.RSI, SignalEvent.RSI_CROSSED_ABOVE_60, null, null, null),
                        new ScanCondition(Timeframe.D1, IndicatorType.SUPERTREND, null, TrendState.SUPERTREND_BULLISH, null, null)
                ),
                null
        );

        ScanResponse response = scanService.execute(request);

        // Expected order:
        // 1. ETH (daysSinceCross: 1)
        // 2. BTC (daysSinceCross: 2)
        // 3. SOL (daysSinceCross: null, daysSinceFlip: 1)
        assertThat(response.results()).hasSize(3);
        assertThat(response.results().get(0).assetSymbol()).isEqualTo("ETHUSDT");
        assertThat(response.results().get(1).assetSymbol()).isEqualTo("BTCUSDT");
        assertThat(response.results().get(2).assetSymbol()).isEqualTo("SOLUSDT");
    }

    @Test
    void execute_CrossTimeframeAND_ReturnsIntersection() {
        Asset btc = new Asset(); btc.setId(1L); btc.setSymbol("BTCUSDT"); btc.setProvider(MarketProvider.BINANCE);
        Asset eth = new Asset(); eth.setId(2L); eth.setSymbol("ETHUSDT"); eth.setProvider(MarketProvider.BINANCE);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // BTC has W1 BULLISH + D1 BULLISH → should match
        SignalState btcW1 = createSignal(btc, IndicatorType.SUPERTREND, SignalEvent.NONE, now);
        btcW1.setTimeframe(Timeframe.W1); btcW1.setTrendState(TrendState.SUPERTREND_BULLISH);
        SignalState btcD1 = createSignal(btc, IndicatorType.SUPERTREND, SignalEvent.NONE, now);
        btcD1.setTimeframe(Timeframe.D1); btcD1.setTrendState(TrendState.SUPERTREND_BULLISH);

        // ETH has W1 BULLISH but NOT D1 BULLISH → should NOT match after AND intersection
        SignalState ethW1 = createSignal(eth, IndicatorType.SUPERTREND, SignalEvent.NONE, now);
        ethW1.setTimeframe(Timeframe.W1); ethW1.setTrendState(TrendState.SUPERTREND_BULLISH);

        when(candleRepository.findLatestCloseTimeForTimeframe("W1")).thenReturn(Optional.of(now));
        when(candleRepository.findLatestCloseTimeForTimeframe("D1")).thenReturn(Optional.of(now));
        when(signalStateRepository.findStateMatches(IndicatorType.SUPERTREND, TrendState.SUPERTREND_BULLISH, Timeframe.W1, null))
                .thenReturn(List.of(btcW1, ethW1));
        when(signalStateRepository.findStateMatches(IndicatorType.SUPERTREND, TrendState.SUPERTREND_BULLISH, Timeframe.D1, null))
                .thenReturn(List.of(btcD1));
        when(signalStateRepository.findLastDifferentStateTime(any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(signalStateRepository.findFirstCurrentStateTimeAfter(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(now));

        ScanRequest request = new ScanRequest(
                ScanOperator.AND,
                List.of(
                        new ScanCondition(Timeframe.W1, IndicatorType.SUPERTREND, null, TrendState.SUPERTREND_BULLISH, null, null),
                        new ScanCondition(Timeframe.D1, IndicatorType.SUPERTREND, null, TrendState.SUPERTREND_BULLISH, null, null)
                ),
                null
        );

        ScanResponse response = scanService.execute(request);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).assetSymbol()).isEqualTo("BTCUSDT");
        assertThat(response.results().get(0).matchedIndicators()).hasSize(2);
        // Both matched indicators carry their respective timeframes
        List<MatchedIndicator> matched = response.results().get(0).matchedIndicators();
        assertThat(matched).anyMatch(mi -> mi.timeframe() == Timeframe.W1);
        assertThat(matched).anyMatch(mi -> mi.timeframe() == Timeframe.D1);
    }

    @Test
    void execute_EventMatch_DaysSinceFlipReflectsCloseTime() {
        // Regression test: daysSinceFlip was hardcoded to 0 for event-based matches
        Asset asset1 = new Asset(); asset1.setId(1L); asset1.setSymbol("BTCUSDT");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime flipTime = now.minusDays(17);

        SignalState s1 = createSignal(asset1, IndicatorType.SUPERTREND, SignalEvent.SUPERTREND_BULLISH_REVERSAL, flipTime);

        when(candleRepository.findLatestCloseTimeForTimeframe("D1")).thenReturn(Optional.of(now));
        // maxDaysSinceFlip=20 → passed as maxDaysSinceEvent to findEventMatches
        when(signalStateRepository.findEventMatches(IndicatorType.SUPERTREND, SignalEvent.SUPERTREND_BULLISH_REVERSAL, Timeframe.D1, now, 20))
                .thenReturn(List.of(s1));
        when(assetLiquidityRepository.findAll()).thenReturn(List.of());

        ScanRequest request = new ScanRequest(
                ScanOperator.AND,
                List.of(new ScanCondition(Timeframe.D1, IndicatorType.SUPERTREND, SignalEvent.SUPERTREND_BULLISH_REVERSAL, null, 20, null)),
                null
        );

        ScanResponse response = scanService.execute(request);

        SupertrendMatch sm = (SupertrendMatch) response.results().get(0).matchedIndicators().get(0);
        assertThat(sm.daysSinceFlip()).isEqualTo(17);
    }

    @Test
    void execute_SupertrendEventMatch_MaxDaysSinceFlipPassedToRepository() {
        // Regression: maxDaysSinceFlip was ignored for event queries — maxDaysSinceCross (null) was passed instead
        Asset asset1 = new Asset(); asset1.setId(1L); asset1.setSymbol("BTCUSDT");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        when(candleRepository.findLatestCloseTimeForTimeframe("D1")).thenReturn(Optional.of(now));
        when(assetLiquidityRepository.findAll()).thenReturn(List.of());

        ScanRequest request = new ScanRequest(
                ScanOperator.AND,
                List.of(new ScanCondition(Timeframe.D1, IndicatorType.SUPERTREND, SignalEvent.SUPERTREND_BULLISH_REVERSAL, null, 16, null)),
                null
        );

        scanService.execute(request);

        // Verify maxDaysSinceFlip=16 is passed to the repo, NOT null (maxDaysSinceCross)
        verify(signalStateRepository).findEventMatches(
                IndicatorType.SUPERTREND, SignalEvent.SUPERTREND_BULLISH_REVERSAL, Timeframe.D1, now, 16);
    }

    private SignalState createSignal(Asset asset, IndicatorType type, SignalEvent event, OffsetDateTime time) {
        SignalState s = new SignalState();
        s.setAsset(asset);
        s.setIndicatorType(type);
        s.setEvent(event);
        s.setCloseTime(time);
        s.setTimeframe(Timeframe.D1);
        return s;
    }
}
