package walshe.projectcolumbo.ingestion;
import walshe.projectcolumbo.marketpulse.MarketPulseService;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.service.RsiComputationService;
import walshe.projectcolumbo.persistence.service.SignalStateService;
import walshe.projectcolumbo.persistence.service.SuperTrendService;

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
    private SignalStateService signalStateService;
    @Mock
    private MarketPulseService marketPulseService;
    @Mock
    private IngestionRunRepository ingestionRunRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private IngestionOrchestrator orchestrator;

    private MarketPipelineService marketPipelineService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        marketPipelineService = new MarketPipelineService(
                candleIngestionService,
                superTrendService,
                rsiComputationService,
                signalStateService,
                marketPulseService,
                ingestionRunRepository,
                assetRepository,
                orchestrator
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
        InOrder inOrder = inOrder(candleIngestionService, superTrendService, rsiComputationService, signalStateService, marketPulseService);
        inOrder.verify(candleIngestionService).ingestDaily();
        inOrder.verify(superTrendService).processAllActiveAssets(eq(Timeframe.D1), anyInt(), any(), eq(false));
        inOrder.verify(rsiComputationService).computeForActiveAssets(eq(Timeframe.D1), anyInt(), eq(false));
        inOrder.verify(signalStateService).detectDaily();
        inOrder.verify(marketPulseService).computeDaily();
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
