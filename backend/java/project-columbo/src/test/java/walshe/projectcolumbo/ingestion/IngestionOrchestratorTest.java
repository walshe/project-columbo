package walshe.projectcolumbo.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import walshe.projectcolumbo.persistence.AssetRepository;
import walshe.projectcolumbo.persistence.MarketProvider;
import walshe.projectcolumbo.persistence.Timeframe;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionOrchestratorTest {

    @Mock
    private CandleIngestionService candleIngestionService;
    @Mock
    private IngestionRunRepository ingestionRunRepository;
    @Mock
    private AssetRepository assetRepository;

    private IngestionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new IngestionOrchestrator(candleIngestionService, ingestionRunRepository, assetRepository);
    }

    @Test
    void shouldThrowExceptionIfAlreadyRunning() {
        // Given
        when(ingestionRunRepository.findFirstByProviderAndTimeframeAndStatusOrderByStartedAtDesc(
                any(), any(), eq(IngestionRunStatus.RUNNING)))
                .thenReturn(Optional.of(new IngestionRun()));

        // When / Then
        assertThatThrownBy(() -> orchestrator.runInternal(MarketProvider.BINANCE, Timeframe.D1))
                .isInstanceOf(IngestionAlreadyRunningException.class);
        
        verify(candleIngestionService, never()).ingestDaily();
    }

    @Test
    void shouldTrackSuccessfulRun() {
        // Given
        when(ingestionRunRepository.findFirstByProviderAndTimeframeAndStatusOrderByStartedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(assetRepository.count()).thenReturn(10L);
        
        // Mocking save to return the run with an ID
        when(ingestionRunRepository.save(any(IngestionRun.class))).thenAnswer(invocation -> {
            IngestionRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                // Simulate initial save (setting ID)
                java.lang.reflect.Field field = IngestionRun.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(run, 1L);
            }
            return run;
        });

        CandleIngestionService.IngestionStats stats = new CandleIngestionService.IngestionStats();
        stats.insertedCount = 5;
        stats.updatedCount = 2;
        stats.skippedCount = 3;
        stats.errorCount = 0;
        when(candleIngestionService.ingestDaily()).thenReturn(stats);

        // When
        IngestionRun result = orchestrator.runInternal(MarketProvider.BINANCE, Timeframe.D1);

        // Then
        assertThat(result.getStatus()).isEqualTo(IngestionRunStatus.SUCCESS);
        assertThat(result.getInsertedCount()).isEqualTo(5);
        assertThat(result.getUpdatedCount()).isEqualTo(2);
        assertThat(result.getSkippedCount()).isEqualTo(3);
        assertThat(result.getErrorCount()).isEqualTo(0);
        assertThat(result.getFinishedAt()).isAfterOrEqualTo(result.getStartedAt());
        assertThat(result.getDurationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldTrackPartialRunWhenErrorsOccur() {
        // Given
        when(ingestionRunRepository.findFirstByProviderAndTimeframeAndStatusOrderByStartedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(ingestionRunRepository.save(any(IngestionRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CandleIngestionService.IngestionStats stats = new CandleIngestionService.IngestionStats();
        stats.insertedCount = 5;
        stats.errorCount = 1;
        stats.firstErrorMessage = "Provider error";
        when(candleIngestionService.ingestDaily()).thenReturn(stats);

        // When
        IngestionRun result = orchestrator.runInternal(MarketProvider.BINANCE, Timeframe.D1);

        // Then
        assertThat(result.getStatus()).isEqualTo(IngestionRunStatus.PARTIAL);
        assertThat(result.getErrorCount()).isEqualTo(1);
        assertThat(result.getErrorSample()).isEqualTo("Provider error");
    }

    @Test
    void shouldTrackFailedRunWhenExceptionThrown() {
        // Given
        when(ingestionRunRepository.findFirstByProviderAndTimeframeAndStatusOrderByStartedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(ingestionRunRepository.save(any(IngestionRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(candleIngestionService.ingestDaily()).thenThrow(new RuntimeException("Unexpected error"));

        // When
        IngestionRun result = orchestrator.runInternal(MarketProvider.BINANCE, Timeframe.D1);

        // Then
        assertThat(result.getStatus()).isEqualTo(IngestionRunStatus.FAILED);
        assertThat(result.getErrorSample()).isEqualTo("Unexpected error");
    }
    
    @Test
    void shouldDeriveFailedStatusWhenNoProgressMade() {
        // Given
        when(ingestionRunRepository.findFirstByProviderAndTimeframeAndStatusOrderByStartedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(ingestionRunRepository.save(any(IngestionRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CandleIngestionService.IngestionStats stats = new CandleIngestionService.IngestionStats();
        stats.errorCount = 5;
        stats.insertedCount = 0;
        stats.updatedCount = 0;
        stats.skippedCount = 0;
        when(candleIngestionService.ingestDaily()).thenReturn(stats);

        // When
        IngestionRun result = orchestrator.runInternal(MarketProvider.BINANCE, Timeframe.D1);

        // Then
        assertThat(result.getStatus()).isEqualTo(IngestionRunStatus.FAILED);
    }
}
