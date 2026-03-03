package walshe.projectcolumbo.api.v1.scan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import walshe.projectcolumbo.api.v1.scan.dto.*;
import walshe.projectcolumbo.persistence.*;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    @Mock
    private SignalStateRepository signalStateRepository;
    @Mock
    private AssetRepository assetRepository;

    private ScanService scanService;

    @BeforeEach
    void setUp() {
        scanService = new ScanService(signalStateRepository, assetRepository);
    }

    @Test
    void execute_SingleCondition_ReturnsCorrectResults() {
        Asset asset1 = new Asset(); asset1.setId(1L); asset1.setSymbol("BTCUSDT");
        Asset asset2 = new Asset(); asset2.setId(2L); asset2.setSymbol("ETHUSDT");
        OffsetDateTime now = OffsetDateTime.now();

        SignalState s1 = createSignal(asset1, IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, now);
        
        when(signalStateRepository.findLatestByCondition(Timeframe.D1, IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL))
                .thenReturn(List.of(s1));

        ScanRequest request = new ScanRequest(
                Timeframe.D1,
                ScanOperator.AND,
                List.of(new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL))
        );

        ScanResponse response = scanService.execute(request);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).assetSymbol()).isEqualTo("BTCUSDT");
        assertThat(response.results().get(0).matchedIndicators()).hasSize(1);
    }

    @Test
    void execute_TwoConditionsAND_ReturnsIntersection() {
        Asset asset1 = new Asset(); asset1.setId(1L); asset1.setSymbol("BTCUSDT");
        Asset asset2 = new Asset(); asset2.setId(2L); asset2.setSymbol("ETHUSDT");
        OffsetDateTime now = OffsetDateTime.now();

        SignalState s1 = createSignal(asset1, IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, now);
        SignalState s2 = createSignal(asset2, IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, now);
        SignalState s3 = createSignal(asset1, IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, now);

        when(signalStateRepository.findLatestByCondition(Timeframe.D1, IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL))
                .thenReturn(List.of(s1, s2));
        when(signalStateRepository.findLatestByCondition(Timeframe.D1, IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60))
                .thenReturn(List.of(s3));

        ScanRequest request = new ScanRequest(
                Timeframe.D1,
                ScanOperator.AND,
                List.of(
                        new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL),
                        new ScanCondition(IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60)
                )
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
        OffsetDateTime now = OffsetDateTime.now();

        SignalState s1 = createSignal(asset1, IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, now);
        SignalState s2 = createSignal(asset2, IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, now);

        when(signalStateRepository.findLatestByCondition(Timeframe.D1, IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL))
                .thenReturn(List.of(s1));
        when(signalStateRepository.findLatestByCondition(Timeframe.D1, IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60))
                .thenReturn(List.of(s2));

        ScanRequest request = new ScanRequest(
                Timeframe.D1,
                ScanOperator.OR,
                List.of(
                        new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL),
                        new ScanCondition(IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60)
                )
        );

        ScanResponse response = scanService.execute(request);

        assertThat(response.results()).hasSize(2);
        assertThat(response.results()).extracting(ScanResult::assetSymbol).containsExactlyInAnyOrder("BTCUSDT", "ETHUSDT");
    }

    @Test
    void execute_NoMatches_ReturnsEmptyList() {
        when(signalStateRepository.findLatestByCondition(Timeframe.D1, IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL))
                .thenReturn(List.of());

        ScanRequest request = new ScanRequest(
                Timeframe.D1,
                ScanOperator.AND,
                List.of(new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL))
        );

        ScanResponse response = scanService.execute(request);

        assertThat(response.results()).isEmpty();
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
