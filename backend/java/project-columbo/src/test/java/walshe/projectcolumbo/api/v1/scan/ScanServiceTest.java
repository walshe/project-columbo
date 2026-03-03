package walshe.projectcolumbo.api.v1.scan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import walshe.projectcolumbo.api.v1.scan.dto.*;
import walshe.projectcolumbo.persistence.*;

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

    private ScanService scanService;

    @BeforeEach
    void setUp() {
        scanService = new ScanService(signalStateRepository, candleRepository, scanValidator, rsiRepository);
    }

    @Test
    void execute_SingleCondition_ReturnsCorrectResults() {
        Asset asset1 = new Asset(); asset1.setId(1L); asset1.setSymbol("BTCUSDT");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        SignalState s1 = createSignal(asset1, IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, now);
        
        when(candleRepository.findLatestCloseTimeForTimeframe("D1")).thenReturn(Optional.of(now));
        when(signalStateRepository.findEventMatches(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, Timeframe.D1, now))
                .thenReturn(List.of(s1));

        ScanRequest request = new ScanRequest(
                Timeframe.D1,
                ScanOperator.AND,
                List.of(new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, null, null)),
                null
        );

        ScanResponse response = scanService.execute(request);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).assetSymbol()).isEqualTo("BTCUSDT");
        assertThat(response.results().get(0).matchedIndicators()).hasSize(1);
        MatchedIndicator mi = response.results().get(0).matchedIndicators().get(0);
        assertThat(mi).isInstanceOf(SupertrendMatch.class);
        SupertrendMatch sm = (SupertrendMatch) mi;
        assertThat(sm.event()).isEqualTo(SignalEvent.BULLISH_REVERSAL);
        verify(scanValidator).validate(request);
    }

    @Test
    void execute_TwoConditionsAND_ReturnsIntersection() {
        Asset asset1 = new Asset(); asset1.setId(1L); asset1.setSymbol("BTCUSDT");
        Asset asset2 = new Asset(); asset2.setId(2L); asset2.setSymbol("ETHUSDT");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        SignalState s1 = createSignal(asset1, IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, now);
        SignalState s2 = createSignal(asset2, IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, now);
        SignalState s3 = createSignal(asset1, IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, now);

        when(candleRepository.findLatestCloseTimeForTimeframe("D1")).thenReturn(Optional.of(now));
        when(signalStateRepository.findEventMatches(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, Timeframe.D1, now))
                .thenReturn(List.of(s1, s2));
        when(signalStateRepository.findEventMatches(IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, Timeframe.D1, now))
                .thenReturn(List.of(s3));

        ScanRequest request = new ScanRequest(
                Timeframe.D1,
                ScanOperator.AND,
                List.of(
                        new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, null, null),
                        new ScanCondition(IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, null, null)
                ),
                null
        );

        ScanResponse response = scanService.execute(request);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).assetSymbol()).isEqualTo("BTCUSDT");
        assertThat(response.results().get(0).matchedIndicators()).hasSize(2);
    }

    @Test
    void execute_TwoConditionsOR_ReturnsUnion() {
        Asset asset1 = new Asset(); asset1.setId(1L); asset1.setSymbol("BTCUSDT");
        Asset asset2 = new Asset(); asset2.setId(2L); asset2.setSymbol("ETHUSDT");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        SignalState s1 = createSignal(asset1, IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, now);
        SignalState s2 = createSignal(asset2, IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, now);

        when(candleRepository.findLatestCloseTimeForTimeframe("D1")).thenReturn(Optional.of(now));
        when(signalStateRepository.findEventMatches(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, Timeframe.D1, now))
                .thenReturn(List.of(s1));
        when(signalStateRepository.findEventMatches(IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, Timeframe.D1, now))
                .thenReturn(List.of(s2));

        ScanRequest request = new ScanRequest(
                Timeframe.D1,
                ScanOperator.OR,
                List.of(
                        new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, null, null),
                        new ScanCondition(IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, null, null)
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
        when(signalStateRepository.findEventMatches(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, Timeframe.D1, now))
                .thenReturn(List.of());

        ScanRequest request = new ScanRequest(
                Timeframe.D1,
                ScanOperator.AND,
                List.of(new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, null, null)),
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
        s1.setTrendState(TrendState.BULLISH);

        when(candleRepository.findLatestCloseTimeForTimeframe("D1")).thenReturn(Optional.of(now));
        when(signalStateRepository.findStateMatches(IndicatorType.SUPERTREND, TrendState.BULLISH, Timeframe.D1, 5))
                .thenReturn(List.of(s1));
        
        // Mocking flip time search
        when(signalStateRepository.findLastDifferentStateTime(eq(1L), eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(TrendState.BULLISH), any()))
                .thenReturn(Optional.of(now.minusDays(3)));
        when(signalStateRepository.findFirstCurrentStateTimeAfter(eq(1L), eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(TrendState.BULLISH), any(), any()))
                .thenReturn(Optional.of(now.minusDays(2)));

        ScanRequest request = new ScanRequest(
                Timeframe.D1,
                ScanOperator.AND,
                List.of(new ScanCondition(IndicatorType.SUPERTREND, null, TrendState.BULLISH, 5)),
                null
        );

        ScanResponse response = scanService.execute(request);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).assetSymbol()).isEqualTo("BTCUSDT");
        MatchedIndicator mi = response.results().get(0).matchedIndicators().get(0);
        assertThat(mi).isInstanceOf(SupertrendMatch.class);
        SupertrendMatch sm = (SupertrendMatch) mi;
        assertThat(sm.state()).isEqualTo(TrendState.BULLISH);
        assertThat(sm.daysSinceFlip()).isEqualTo(2);
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
