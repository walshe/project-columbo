package walshe.projectcolumbo.ingestion;
import walshe.projectcolumbo.marketpulse.MarketPulseService;
import walshe.projectcolumbo.marketpulse.W1IndicatorService;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.service.EmaComputationService;
import walshe.projectcolumbo.persistence.service.ElderImpulseStateService;
import walshe.projectcolumbo.persistence.service.MacdComputationService;
import walshe.projectcolumbo.persistence.service.RsiComputationService;
import walshe.projectcolumbo.persistence.service.SignalStateService;
import walshe.projectcolumbo.persistence.service.SuperTrendService;
import walshe.projectcolumbo.persistence.service.ThermometerService;
import walshe.projectcolumbo.persistence.service.ThermometerStateService;
import walshe.projectcolumbo.rollup.CandleRollupService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MarketPipelineServiceTest {

    @Mock
    private CandleIngestionService candleIngestionService;
    @Mock
    private SuperTrendService superTrendService;
    @Mock
    private RsiComputationService rsiComputationService;
    @Mock
    private EmaComputationService emaComputationService;
    @Mock
    private MacdComputationService macdComputationService;
    @Mock
    private ElderImpulseStateService elderImpulseStateService;
    @Mock
    private ThermometerService thermometerService;
    @Mock
    private ThermometerStateService thermometerStateService;
    @Mock
    private SignalStateService signalStateService;
    @Mock
    private MarketPulseService marketPulseService;
    @Mock
    private IngestionRunRepository ingestionRunRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private IngestionOrchestrator orchestrator;
    @Mock
    private CandleRollupService candleRollupService;
    @Mock
    private W1IndicatorService w1IndicatorService;

    private MarketPipelineService marketPipelineService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        marketPipelineService = new MarketPipelineService(
                candleIngestionService,
                superTrendService,
                rsiComputationService,
                emaComputationService,
                macdComputationService,
                elderImpulseStateService,
                thermometerService,
                thermometerStateService,
                signalStateService,
                marketPulseService,
                ingestionRunRepository,
                assetRepository,
                orchestrator,
                candleRollupService,
                w1IndicatorService
        );

        // Default behavior: no running ingestion
        when(ingestionRunRepository.findFirstByProviderAndTimeframeAndStatusOrderByStartedAtDesc(
                any(), any(), eq(IngestionRunStatus.RUNNING))).thenReturn(Optional.empty());
        
        // Mock saving the run
        when(ingestionRunRepository.save(any(IngestionRun.class))).thenAnswer(i -> {
            IngestionRun run = i.getArgument(0);
            if (run.getId() == null) {
                run.setId(123L);
            }
            return run;
        });

        // Mock stats
        when(candleIngestionService.ingestDaily()).thenReturn(new CandleIngestionService.IngestionStats());
    }

    @Test
    void runDaily_shouldExecutePhasesInCorrectOrder() {
        // When
        marketPipelineService.runDaily(MarketProvider.BINANCE, Timeframe.D1, RunMode.INCREMENTAL);

        // Then
        InOrder inOrder = inOrder(candleIngestionService, superTrendService, rsiComputationService,
                emaComputationService, macdComputationService, thermometerService,
                signalStateService, elderImpulseStateService, thermometerStateService,
                marketPulseService, candleRollupService, w1IndicatorService);
        inOrder.verify(candleIngestionService).ingestDaily();
        inOrder.verify(superTrendService).processAllActiveAssets(eq(Timeframe.D1), anyInt(), any(), eq(false));
        inOrder.verify(rsiComputationService).computeForActiveAssets(eq(Timeframe.D1), anyInt(), eq(false));
        inOrder.verify(emaComputationService).computeForActiveAssets(eq(Timeframe.D1), eq(13), eq(false));
        inOrder.verify(macdComputationService).computeForActiveAssets(eq(Timeframe.D1), eq(false));
        inOrder.verify(thermometerService).computeForActiveAssets(eq(false));
        inOrder.verify(signalStateService).detectForTimeframe(eq(Timeframe.D1));
        inOrder.verify(elderImpulseStateService).computeForAllActiveAssets(eq(Timeframe.D1));
        inOrder.verify(thermometerStateService).computeForAllActiveAssets();
        inOrder.verify(marketPulseService).computeDaily();
        inOrder.verify(candleRollupService).rollupForAllActiveAssets(eq(Timeframe.D1), eq(Timeframe.W1), any());
        inOrder.verify(w1IndicatorService).processAllActiveAssets();
    }

    @Test
    void runDaily_shouldStopOnFailure() {
        // Given: Ingestion fails
        when(candleIngestionService.ingestDaily()).thenThrow(new RuntimeException("Ingestion failed"));

        // When
        marketPipelineService.runDaily(MarketProvider.BINANCE, Timeframe.D1, RunMode.INCREMENTAL);

        // Then
        verify(candleIngestionService).ingestDaily();
        verifyNoInteractions(superTrendService, rsiComputationService, signalStateService, marketPulseService);
        
        // Verify orchestrator.finalizeRun was called with error
        verify(orchestrator).finalizeRun(any(IngestionRun.class), isNull(), any(Exception.class));
    }

    @Test
    void runDaily_shouldRejectIfAlreadyRunning() {
        // Given
        IngestionRun running = new IngestionRun();
        running.setStatus(IngestionRunStatus.RUNNING);
        when(ingestionRunRepository.findFirstByProviderAndTimeframeAndStatusOrderByStartedAtDesc(
                any(), any(), eq(IngestionRunStatus.RUNNING))).thenReturn(Optional.of(running));

        // When / Then
        assertThrows(IngestionAlreadyRunningException.class, () -> 
                marketPipelineService.runDaily(MarketProvider.BINANCE, Timeframe.D1, RunMode.INCREMENTAL));
        
        verifyNoInteractions(candleIngestionService, superTrendService, rsiComputationService, signalStateService, marketPulseService);
    }
}
