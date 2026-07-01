package walshe.projectcolumbo.api.v1.summary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import walshe.projectcolumbo.api.v1.SignalQueryService;
import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.api.v1.summary.dto.ConfluenceSummaryReport;
import walshe.projectcolumbo.ingestion.IngestionStatusService;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.TrendState;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ConfluenceSummaryServiceTest {

    @Mock
    private SignalQueryService signalQueryService;

    @Mock
    private IngestionStatusService ingestionStatusService;

    @Mock
    private walshe.projectcolumbo.api.v1.CandleFreshnessService freshnessService;

    private ConfluenceSummaryService service;

    private static SignalStateDto dto(String symbol, TrendState state, long daysSinceFlip) {
        return new SignalStateDto(symbol, state,
                OffsetDateTime.now().minusDays(daysSinceFlip), daysSinceFlip,
                BigDecimal.valueOf(1000), "http://tv/" + symbol, null);
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ConfluenceSummaryService(signalQueryService, ingestionStatusService, freshnessService);
        when(ingestionStatusService.lastSuccessfulD1IngestionAt()).thenReturn(Optional.empty());
        when(ingestionStatusService.latestCandleDate()).thenReturn(Optional.empty());
        // default: all queries return empty
        when(signalQueryService.listSignals(any(), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void includesAssetAlignedOnBothTimeframes() {
        SignalStateDto btc = dto("BTC/USDT", TrendState.SUPERTREND_BULLISH, 2);
        when(signalQueryService.listSignals(eq(Timeframe.W1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BULLISH), any()))
                .thenReturn(List.of(btc));
        when(signalQueryService.listSignals(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BULLISH), any()))
                .thenReturn(List.of(btc));

        ConfluenceSummaryReport report = service.getConfluence(7);

        assertThat(report.bullishConfluence()).hasSize(1);
        assertThat(report.bullishConfluence().get(0).symbol()).isEqualTo("BTC/USDT");
        assertThat(report.bearishConfluence()).isEmpty();
    }

    @Test
    void excludesAssetAlignedOnOnlyOneTimeframe() {
        when(signalQueryService.listSignals(eq(Timeframe.W1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BULLISH), any()))
                .thenReturn(List.of(dto("ETH/USDT", TrendState.SUPERTREND_BULLISH, 1)));
        when(signalQueryService.listSignals(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BULLISH), any()))
                .thenReturn(List.of(dto("BTC/USDT", TrendState.SUPERTREND_BULLISH, 1))); // different symbol

        ConfluenceSummaryReport report = service.getConfluence(7);

        assertThat(report.bullishConfluence()).isEmpty();
    }

    @Test
    void ordersResultsByD1FlipDateDescending() {
        SignalStateDto assetA = dto("AAA/USDT", TrendState.SUPERTREND_BULLISH, 5);
        SignalStateDto assetB = dto("BBB/USDT", TrendState.SUPERTREND_BULLISH, 1);

        when(signalQueryService.listSignals(eq(Timeframe.W1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BULLISH), any()))
                .thenReturn(List.of(assetA, assetB));
        when(signalQueryService.listSignals(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BULLISH), any()))
                .thenReturn(List.of(assetB, assetA)); // LAST_FLIP_DESC: newer first

        ConfluenceSummaryReport report = service.getConfluence(7);

        assertThat(report.bullishConfluence()).hasSize(2);
        assertThat(report.bullishConfluence().get(0).symbol()).isEqualTo("BBB/USDT"); // newer first
        assertThat(report.bullishConfluence().get(1).symbol()).isEqualTo("AAA/USDT");
    }

    @Test
    void returnsEmptyListsWhenNoConfluenceExists() {
        ConfluenceSummaryReport report = service.getConfluence(7);

        assertThat(report.bullishConfluence()).isEmpty();
        assertThat(report.bearishConfluence()).isEmpty();
        assertThat(report.bullishRetest()).isEmpty();
        assertThat(report.bearishRetest()).isEmpty();
    }

    @Test
    void includesAssetInBullishRetestWhenD1CounterTrendWithinWindow() {
        SignalStateDto w1Bullish = dto("SOL/USDT", TrendState.SUPERTREND_BULLISH, 10);
        SignalStateDto d1Bearish = dto("SOL/USDT", TrendState.SUPERTREND_BEARISH, 3);

        when(signalQueryService.listSignals(eq(Timeframe.W1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BULLISH), any()))
                .thenReturn(List.of(w1Bullish));
        when(signalQueryService.listSignals(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BEARISH), any()))
                .thenReturn(List.of(d1Bearish));

        ConfluenceSummaryReport report = service.getConfluence(7);

        assertThat(report.bullishRetest()).hasSize(1);
        assertThat(report.bullishRetest().get(0).symbol()).isEqualTo("SOL/USDT");
        assertThat(report.bullishConfluence()).isEmpty();
    }

    @Test
    void excludesAssetFromRetestWhenD1CounterTrendExceedsWindow() {
        SignalStateDto w1Bullish = dto("SOL/USDT", TrendState.SUPERTREND_BULLISH, 20);
        SignalStateDto d1Bearish = dto("SOL/USDT", TrendState.SUPERTREND_BEARISH, 10); // 10 days > 7

        when(signalQueryService.listSignals(eq(Timeframe.W1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BULLISH), any()))
                .thenReturn(List.of(w1Bullish));
        when(signalQueryService.listSignals(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BEARISH), any()))
                .thenReturn(List.of(d1Bearish));

        ConfluenceSummaryReport report = service.getConfluence(7);

        assertThat(report.bullishRetest()).isEmpty();
    }

    @Test
    void includesAssetInBearishRetestWhenD1CounterTrendWithinWindow() {
        SignalStateDto w1Bearish = dto("ETH/USDT", TrendState.SUPERTREND_BEARISH, 10);
        SignalStateDto d1Bullish = dto("ETH/USDT", TrendState.SUPERTREND_BULLISH, 2);

        when(signalQueryService.listSignals(eq(Timeframe.W1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BEARISH), any()))
                .thenReturn(List.of(w1Bearish));
        when(signalQueryService.listSignals(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BULLISH), any()))
                .thenReturn(List.of(d1Bullish));

        ConfluenceSummaryReport report = service.getConfluence(7);

        assertThat(report.bearishRetest()).hasSize(1);
        assertThat(report.bearishRetest().get(0).symbol()).isEqualTo("ETH/USDT");
        assertThat(report.bearishConfluence()).isEmpty();
    }
}
