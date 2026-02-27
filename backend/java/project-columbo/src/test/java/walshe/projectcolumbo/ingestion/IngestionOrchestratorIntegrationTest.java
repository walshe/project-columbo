package walshe.projectcolumbo.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import walshe.projectcolumbo.TestcontainersConfiguration;
import walshe.projectcolumbo.persistence.Asset;
import walshe.projectcolumbo.persistence.AssetRepository;
import walshe.projectcolumbo.persistence.MarketProvider;
import walshe.projectcolumbo.persistence.Timeframe;
import walshe.projectcolumbo.persistence.RsiRepository;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class IngestionOrchestratorIntegrationTest {

    @Autowired
    private IngestionOrchestrator orchestrator;

    @Autowired
    private IngestionRunRepository ingestionRunRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private RsiRepository rsiRepository;

    @MockitoBean
    private CandleIngestionService candleIngestionService;

    @BeforeEach
    void setUp() {
        ingestionRunRepository.deleteAll();
        rsiRepository.deleteAll();
        assetRepository.deleteAll();
    }

    @Test
    void shouldCreateRunRecordAndTrackStatus() {
        // Given
        assetRepository.save(new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true));
        
        CandleIngestionService.IngestionStats stats = new CandleIngestionService.IngestionStats();
        stats.insertedCount = 100;
        stats.errorCount = 0;
        when(candleIngestionService.ingestDaily()).thenReturn(stats);

        // When
        IngestionRun run = orchestrator.runInternal(MarketProvider.BINANCE, Timeframe.D1);

        // Then
        assertThat(run.getId()).isNotNull();
        assertThat(run.getStatus()).isEqualTo(IngestionRunStatus.SUCCESS);
        assertThat(run.getAssetCount()).isEqualTo(1);
        assertThat(run.getInsertedCount()).isEqualTo(100);
        assertThat(run.getFinishedAt()).isNotNull();

        List<IngestionRun> allRuns = ingestionRunRepository.findAll();
        assertThat(allRuns).hasSize(1);
        assertThat(allRuns.get(0).getStatus()).isEqualTo(IngestionRunStatus.SUCCESS);
    }

    @Test
    void shouldPreventConcurrentRuns() {
        // Given
        IngestionRun existing = new IngestionRun();
        existing.setProvider(MarketProvider.BINANCE);
        existing.setTimeframe(Timeframe.D1);
        existing.setStatus(IngestionRunStatus.RUNNING);
        existing.setStartedAt(OffsetDateTime.now());
        existing.setAssetCount(1);
        ingestionRunRepository.save(existing);

        // When / Then
        assertThatThrownBy(() -> orchestrator.runInternal(MarketProvider.BINANCE, Timeframe.D1))
                .isInstanceOf(IngestionAlreadyRunningException.class);
    }

    @Test
    void shouldTrackPartialStatusOnError() {
        // Given
        assetRepository.save(new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true));

        CandleIngestionService.IngestionStats stats = new CandleIngestionService.IngestionStats();
        stats.insertedCount = 10;
        stats.errorCount = 1;
        stats.firstErrorMessage = "Timeout";
        when(candleIngestionService.ingestDaily()).thenReturn(stats);

        // When
        IngestionRun run = orchestrator.runInternal(MarketProvider.BINANCE, Timeframe.D1);

        // Then
        assertThat(run.getStatus()).isEqualTo(IngestionRunStatus.PARTIAL);
        assertThat(run.getErrorCount()).isEqualTo(1);
        assertThat(run.getErrorSample()).isEqualTo("Timeout");
    }
}
