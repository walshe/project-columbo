package walshe.projectcolumbo.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketPulseServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private SignalStateRepository signalStateRepository;

    @Mock
    private MarketBreadthSnapshotRepository snapshotRepository;

    @InjectMocks
    private MarketPulseService service;

    @Test
    void shouldComputeDailyWithCorrectRatio() {
        // Given
        when(assetRepository.countByActiveTrue()).thenReturn(60L);
        
        Asset asset1 = new Asset("BTC", "Bitcoin", MarketProvider.BINANCE, true);
        OffsetDateTime closeTime = OffsetDateTime.of(2026, 2, 25, 23, 59, 59, 999000000, ZoneOffset.UTC);
        
        // 21 bullish, 20 bearish = 41 total present. 19 missing.
        // User example: 21 bullish, 20 bearish, 19 missing, 60 total.
        // New Ratio = 21 / 41 = 0.5122
        
        List<SignalState> states = new java.util.ArrayList<>();
        for (int i = 0; i < 21; i++) {
            states.add(new SignalState(asset1, Timeframe.D1, IndicatorType.SUPERTREND, closeTime, TrendState.BULLISH, SignalEvent.NONE));
        }
        for (int i = 0; i < 20; i++) {
            states.add(new SignalState(asset1, Timeframe.D1, IndicatorType.SUPERTREND, closeTime, TrendState.BEARISH, SignalEvent.NONE));
        }
        
        when(signalStateRepository.findLatestFinalizedForActiveAssets(any(), any(), any())).thenReturn(states);
        when(snapshotRepository.findByTimeframeAndIndicatorTypeAndSnapshotCloseTime(any(), any(), any())).thenReturn(Optional.empty());

        // When
        service.computeDaily();

        // Then
        ArgumentCaptor<MarketBreadthSnapshot> captor = ArgumentCaptor.forClass(MarketBreadthSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        
        MarketBreadthSnapshot snapshot = captor.getValue();
        assertThat(snapshot.getBullishCount()).isEqualTo(21);
        assertThat(snapshot.getBearishCount()).isEqualTo(20);
        assertThat(snapshot.getMissingCount()).isEqualTo(19);
        assertThat(snapshot.getTotalAssets()).isEqualTo(60);
        
        // 21 / 41 = 0.512195... rounded to 4 decimals HALF_UP -> 0.5122
        assertThat(snapshot.getBullishRatio()).isEqualByComparingTo("0.5122");
    }

    @Test
    void shouldHandleZeroPresentAssets() {
        // Given
        when(assetRepository.countByActiveTrue()).thenReturn(60L);
        when(signalStateRepository.findLatestFinalizedForActiveAssets(any(), any(), any())).thenReturn(List.of());

        // When
        service.computeDaily();

        // Then
        verify(snapshotRepository, never()).save(any());
    }
}
