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
        when(signalStateRepository.findEarliestFinalizedForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(boundary)))
                .thenReturn(List.of());

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
        when(signalStateRepository.findEarliestFinalizedForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(boundary)))
                .thenReturn(List.of());

        List<SignalStateDto> result = service.listSignals(Timeframe.D1, IndicatorType.SUPERTREND, TrendState.BULLISH, SignalSort.ASSET_ASC);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).symbol()).isEqualTo("BTC");
    }
    
    @Test
    void shouldFallbackToEarliestStateIfNoFlipFound() {
        Asset bch = new Asset("BCH", "Bitcoin Cash", MarketProvider.BINANCE, true);
        bch.setId(9L);
        
        OffsetDateTime earliestTime = boundary.minusDays(30);
        SignalState bchLatest = new SignalState(bch, Timeframe.D1, IndicatorType.SUPERTREND, boundary.minusDays(1), TrendState.BEARISH, SignalEvent.NONE);
        SignalState bchEarliest = new SignalState(bch, Timeframe.D1, IndicatorType.SUPERTREND, earliestTime, TrendState.BEARISH, SignalEvent.NONE);

        when(signalStateRepository.findLatestFinalizedForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(boundary)))
                .thenReturn(List.of(bchLatest));
        when(signalStateRepository.findLatestFinalizedFlipsForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(boundary)))
                .thenReturn(List.of());
        when(signalStateRepository.findEarliestFinalizedForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(boundary)))
                .thenReturn(List.of(bchEarliest));

        List<SignalStateDto> result = service.listSignals(Timeframe.D1, IndicatorType.SUPERTREND, null, null);
        
        assertThat(result).hasSize(1);
        assertThat(result.get(0).symbol()).isEqualTo("BCH");
        assertThat(result.get(0).lastFlipTime()).isEqualTo(earliestTime);
        assertThat(result.get(0).daysSinceFlip()).isNotNull();
    }

    @Test
    void shouldIncludeUnknownStatesWhenNoFilterApplied() {
        Asset btc = new Asset("BTC", "Bitcoin", MarketProvider.BINANCE, true);
        btc.setId(1L);
        Asset xrp = new Asset("XRP", "Ripple", MarketProvider.BINANCE, true);
        xrp.setId(3L);

        SignalState btcLatest = new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, boundary.minusDays(1), TrendState.BULLISH, SignalEvent.NONE);
        SignalState xrpLatest = new SignalState(xrp, Timeframe.D1, IndicatorType.SUPERTREND, boundary.minusDays(1), TrendState.UNKNOWN, SignalEvent.NONE);

        when(signalStateRepository.findLatestFinalizedForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(boundary)))
                .thenReturn(List.of(btcLatest, xrpLatest));
        when(signalStateRepository.findLatestFinalizedFlipsForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(boundary)))
                .thenReturn(List.of());
        when(signalStateRepository.findEarliestFinalizedForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(boundary)))
                .thenReturn(List.of());

        List<SignalStateDto> result = service.listSignals(Timeframe.D1, IndicatorType.SUPERTREND, null, SignalSort.ASSET_ASC);
        assertThat(result).hasSize(2);
        assertThat(result).anyMatch(s -> s.symbol().equals("XRP") && s.trendState() == TrendState.UNKNOWN);
        assertThat(result).anyMatch(s -> s.symbol().equals("BTC") && s.trendState() == TrendState.BULLISH);
    }

    @Test
    void shouldFilterByUnknownTrendState() {
        Asset btc = new Asset("BTC", "Bitcoin", MarketProvider.BINANCE, true);
        btc.setId(1L);
        Asset xrp = new Asset("XRP", "Ripple", MarketProvider.BINANCE, true);
        xrp.setId(3L);

        SignalState btcLatest = new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, boundary.minusDays(1), TrendState.BULLISH, SignalEvent.NONE);
        SignalState xrpLatest = new SignalState(xrp, Timeframe.D1, IndicatorType.SUPERTREND, boundary.minusDays(1), TrendState.UNKNOWN, SignalEvent.NONE);

        when(signalStateRepository.findLatestFinalizedForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(boundary)))
                .thenReturn(List.of(btcLatest, xrpLatest));
        when(signalStateRepository.findLatestFinalizedFlipsForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(boundary)))
                .thenReturn(List.of());
        when(signalStateRepository.findEarliestFinalizedForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(boundary)))
                .thenReturn(List.of());

        List<SignalStateDto> result = service.listSignals(Timeframe.D1, IndicatorType.SUPERTREND, TrendState.UNKNOWN, SignalSort.ASSET_ASC);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).symbol()).isEqualTo("XRP");
        assertThat(result.get(0).trendState()).isEqualTo(TrendState.UNKNOWN);
    }

    @Test
    void shouldSortUnknownStatesCorrectByFlipTime() {
        Asset btc = new Asset("BTC", "Bitcoin", MarketProvider.BINANCE, true);
        btc.setId(1L);
        Asset xrp = new Asset("XRP", "Ripple", MarketProvider.BINANCE, true);
        xrp.setId(3L);

        OffsetDateTime btcLatestTime = boundary.minusDays(1);
        OffsetDateTime xrpLatestTime = boundary.minusDays(1);
        OffsetDateTime btcFlipTime = boundary.minusDays(5);
        OffsetDateTime xrpEarliestTime = boundary.minusDays(10);

        SignalState btcLatest = new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, btcLatestTime, TrendState.BULLISH, SignalEvent.NONE);
        SignalState xrpLatest = new SignalState(xrp, Timeframe.D1, IndicatorType.SUPERTREND, xrpLatestTime, TrendState.UNKNOWN, SignalEvent.NONE);

        SignalState btcFlip = new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, btcFlipTime, TrendState.BULLISH, SignalEvent.BULLISH_REVERSAL);
        SignalState xrpEarliest = new SignalState(xrp, Timeframe.D1, IndicatorType.SUPERTREND, xrpEarliestTime, TrendState.UNKNOWN, SignalEvent.NONE);

        when(signalStateRepository.findLatestFinalizedForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(boundary)))
                .thenReturn(List.of(btcLatest, xrpLatest));
        when(signalStateRepository.findLatestFinalizedFlipsForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(boundary)))
                .thenReturn(List.of(btcFlip));
        when(signalStateRepository.findEarliestFinalizedForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(boundary)))
                .thenReturn(List.of(xrpEarliest));

        // Test LAST_FLIP_DESC (newest first)
        List<SignalStateDto> result = service.listSignals(Timeframe.D1, IndicatorType.SUPERTREND, null, SignalSort.LAST_FLIP_DESC);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).symbol()).isEqualTo("BTC"); // 5 days ago (newest)
        assertThat(result.get(1).symbol()).isEqualTo("XRP"); // 10 days ago (oldest)

        // Test LAST_FLIP_ASC (oldest first)
        result = service.listSignals(Timeframe.D1, IndicatorType.SUPERTREND, null, SignalSort.LAST_FLIP_ASC);
        assertThat(result.get(0).symbol()).isEqualTo("XRP"); // 10 days ago
        assertThat(result.get(1).symbol()).isEqualTo("BTC"); // 5 days ago
    }

    @Test
    void shouldSortByTrendState() {
        Asset btc = new Asset("BTC", "Bitcoin", MarketProvider.BINANCE, true);
        btc.setId(1L);
        Asset xrp = new Asset("XRP", "Ripple", MarketProvider.BINANCE, true);
        xrp.setId(3L);
        Asset eth = new Asset("ETH", "Ethereum", MarketProvider.BINANCE, true);
        eth.setId(2L);

        SignalState btcLatest = new SignalState(btc, Timeframe.D1, IndicatorType.SUPERTREND, boundary, TrendState.BULLISH, SignalEvent.NONE);
        SignalState xrpLatest = new SignalState(xrp, Timeframe.D1, IndicatorType.SUPERTREND, boundary, TrendState.UNKNOWN, SignalEvent.NONE);
        SignalState ethLatest = new SignalState(eth, Timeframe.D1, IndicatorType.SUPERTREND, boundary, TrendState.BEARISH, SignalEvent.NONE);

        when(signalStateRepository.findLatestFinalizedForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(boundary)))
                .thenReturn(List.of(btcLatest, xrpLatest, ethLatest));
        when(signalStateRepository.findLatestFinalizedFlipsForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(boundary)))
                .thenReturn(List.of());
        when(signalStateRepository.findEarliestFinalizedForActiveAssets(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(boundary)))
                .thenReturn(List.of());

        List<SignalStateDto> result = service.listSignals(Timeframe.D1, IndicatorType.SUPERTREND, null, SignalSort.TREND_STATE_ASC);
        
        assertThat(result).hasSize(3);
        // TrendState order: BULLISH, BEARISH, UNKNOWN (based on enum ordinal)
        assertThat(result.get(0).trendState()).isEqualTo(TrendState.BULLISH);
        assertThat(result.get(1).trendState()).isEqualTo(TrendState.BEARISH);
        assertThat(result.get(2).trendState()).isEqualTo(TrendState.UNKNOWN);
    }
}
