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

    private ConfluenceSummaryService service;

    private static SignalStateDto dto(String symbol, OffsetDateTime flipTime) {
        return new SignalStateDto(symbol, TrendState.SUPERTREND_BULLISH, flipTime,
                flipTime != null ? 1L : null, BigDecimal.valueOf(1000), "http://tv/" + symbol);
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ConfluenceSummaryService(signalQueryService, ingestionStatusService);
        when(ingestionStatusService.lastSuccessfulD1IngestionAt()).thenReturn(Optional.empty());
        when(ingestionStatusService.latestCandleDate()).thenReturn(Optional.empty());
    }

    @Test
    void includesAssetAlignedOnBothTimeframes() {
        OffsetDateTime flipTime = OffsetDateTime.now().minusDays(2);
        when(signalQueryService.listSignals(eq(Timeframe.W1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BULLISH), any()))
                .thenReturn(List.of(dto("BTC/USDT", flipTime)));
        when(signalQueryService.listSignals(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BULLISH), any()))
                .thenReturn(List.of(dto("BTC/USDT", flipTime)));
        when(signalQueryService.listSignals(eq(Timeframe.W1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BEARISH), any()))
                .thenReturn(List.of());
        when(signalQueryService.listSignals(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BEARISH), any()))
                .thenReturn(List.of());

        ConfluenceSummaryReport report = service.getConfluence();

        assertThat(report.bullishConfluence()).hasSize(1);
        assertThat(report.bullishConfluence().get(0).symbol()).isEqualTo("BTC/USDT");
        assertThat(report.bearishConfluence()).isEmpty();
    }

    @Test
    void excludesAssetAlignedOnOnlyOneTimeframe() {
        OffsetDateTime flipTime = OffsetDateTime.now().minusDays(1);
        when(signalQueryService.listSignals(eq(Timeframe.W1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BULLISH), any()))
                .thenReturn(List.of(dto("ETH/USDT", flipTime)));
        when(signalQueryService.listSignals(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BULLISH), any()))
                .thenReturn(List.of(dto("BTC/USDT", flipTime))); // different symbol
        when(signalQueryService.listSignals(eq(Timeframe.W1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BEARISH), any()))
                .thenReturn(List.of());
        when(signalQueryService.listSignals(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BEARISH), any()))
                .thenReturn(List.of());

        ConfluenceSummaryReport report = service.getConfluence();

        assertThat(report.bullishConfluence()).isEmpty();
    }

    @Test
    void ordersResultsByD1FlipDateDescending() {
        OffsetDateTime older = OffsetDateTime.now().minusDays(5);
        OffsetDateTime newer = OffsetDateTime.now().minusDays(1);

        SignalStateDto assetA = new SignalStateDto("AAA/USDT", TrendState.SUPERTREND_BULLISH, older, 5L, BigDecimal.valueOf(500), null);
        SignalStateDto assetB = new SignalStateDto("BBB/USDT", TrendState.SUPERTREND_BULLISH, newer, 1L, BigDecimal.valueOf(500), null);

        when(signalQueryService.listSignals(eq(Timeframe.W1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BULLISH), any()))
                .thenReturn(List.of(assetA, assetB));
        when(signalQueryService.listSignals(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BULLISH), any()))
                .thenReturn(List.of(assetA, assetB));
        when(signalQueryService.listSignals(eq(Timeframe.W1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BEARISH), any()))
                .thenReturn(List.of());
        when(signalQueryService.listSignals(eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), eq(TrendState.SUPERTREND_BEARISH), any()))
                .thenReturn(List.of());

        ConfluenceSummaryReport report = service.getConfluence();

        assertThat(report.bullishConfluence()).hasSize(2);
        assertThat(report.bullishConfluence().get(0).symbol()).isEqualTo("BBB/USDT"); // newer first
        assertThat(report.bullishConfluence().get(1).symbol()).isEqualTo("AAA/USDT");
    }

    @Test
    void returnsEmptyListsWhenNoConfluenceExists() {
        when(signalQueryService.listSignals(any(), any(), any(), any())).thenReturn(List.of());

        ConfluenceSummaryReport report = service.getConfluence();

        assertThat(report.bullishConfluence()).isEmpty();
        assertThat(report.bearishConfluence()).isEmpty();
    }
}
