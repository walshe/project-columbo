package walshe.projectcolumbo.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import walshe.projectcolumbo.api.v1.dto.SignalSort;
import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.config.TimeProvider;
import walshe.projectcolumbo.persistence.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignalQueryServiceTest {

    @Mock
    private SignalStateRepository signalStateRepository;

    @Mock
    private TimeProvider timeProvider;

    private SignalQueryService service;

    private final OffsetDateTime now = OffsetDateTime.of(2024, 1, 10, 12, 0, 0, 0, ZoneOffset.UTC);
    private final OffsetDateTime boundary = now.truncatedTo(ChronoUnit.DAYS);

    @BeforeEach
    void setUp() {
        service = new SignalQueryService(signalStateRepository, timeProvider);
        when(timeProvider.now()).thenReturn(now);
    }

    @Test
    void shouldListSignalsAndApplySorting() {
        Asset btc = new Asset("BTC", "Bitcoin", MarketProvider.BINANCE, true);
        btc.setId(1L);
        Asset eth = new Asset("ETH", "Ethereum", MarketProvider.BINANCE, true);
        eth.setId(2L);

        SignalState btcLatest = new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, boundary.minusDays(1), TrendState.BULLISH, SignalEvent.NONE);
        SignalState ethLatest = new SignalState(eth, Timeframe.D1, IndicatorType.SUPERTREND, boundary.minusDays(1), TrendState.BEARISH, SignalEvent.NONE);

        SignalState btcFlip = new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, boundary.minusDays(5), TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL);
        SignalState ethFlip = new SignalState(eth, Timeframe.D1, IndicatorType.SUPERTREND, boundary.minusDays(2), TrendState.BEARISH, SignalEvent.BEARISH_REVERSAL);

        when(signalStateRepository.findLatestFinalizedForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(boundary)))
                .thenReturn(List.of(btcLatest, ethLatest));
        when(signalStateRepository.findLatestFinalizedFlipsForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(boundary)))
                .thenReturn(List.of(btcFlip, ethFlip));

        // Test ASSET_ASC
        List<SignalStateDto> result = service.listSignals(Timeframe.D1, IndicatorType.SUPERTREND, null, SignalSort.ASSET_ASC);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).symbol()).isEqualTo("BTC");
        assertThat(result.get(1).symbol()).isEqualTo("ETH");

        // Test LAST_FLIP_ASC (oldest first)
        result = service.listSignals(Timeframe.D1, IndicatorType.SUPERTREND, null, SignalSort.LAST_FLIP_ASC);
        assertThat(result.get(0).symbol()).isEqualTo("BTC"); // 5 days ago
        assertThat(result.get(1).symbol()).isEqualTo("ETH"); // 2 days ago

        // Test LAST_FLIP_DESC (newest first)
        result = service.listSignals(Timeframe.D1, IndicatorType.SUPERTREND, null, SignalSort.LAST_FLIP_DESC);
        assertThat(result.get(0).symbol()).isEqualTo("ETH"); // 2 days ago
        assertThat(result.get(1).symbol()).isEqualTo("BTC"); // 5 days ago
    }

    @Test
    void shouldFilterByTrendState() {
        Asset btc = new Asset("BTC", "Bitcoin", MarketProvider.BINANCE, true);
        btc.setId(1L);
        Asset eth = new Asset("ETH", "Ethereum", MarketProvider.BINANCE, true);
        eth.setId(2L);

        SignalState btcLatest = new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, boundary.minusDays(1), TrendState.BULLISH, SignalEvent.NONE);
        SignalState ethLatest = new SignalState(eth, Timeframe.D1, IndicatorType.SUPERTREND, boundary.minusDays(1), TrendState.BEARISH, SignalEvent.NONE);

        when(signalStateRepository.findLatestFinalizedForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(boundary)))
                .thenReturn(List.of(btcLatest, ethLatest));
        when(signalStateRepository.findLatestFinalizedFlipsForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(boundary)))
                .thenReturn(List.of());

        List<SignalStateDto> result = service.listSignals(Timeframe.D1, IndicatorType.SUPERTREND, TrendState.BULLISH, SignalSort.ASSET_ASC);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).symbol()).isEqualTo("BTC");
    }
}
