package walshe.projectcolumbo.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import walshe.projectcolumbo.TestcontainersConfiguration;
import walshe.projectcolumbo.persistence.MarketProvider;
import walshe.projectcolumbo.persistence.Timeframe;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class IngestionRunRepositoryTest {

    @Autowired
    private IngestionRunRepository ingestionRunRepository;

    @BeforeEach
    void setUp() {
        ingestionRunRepository.deleteAll();
    }

    @Test
    void shouldSaveAndRetrieveIngestionRun() {
        // Given
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        IngestionRun run = new IngestionRun();
        run.setProvider(MarketProvider.BINANCE);
        run.setTimeframe(Timeframe.D1);
        run.setStartedAt(now);
        run.setStatus(IngestionRunStatus.RUNNING);
        run.setAssetCount(10);

        // When
        IngestionRun saved = ingestionRunRepository.save(run);

        // Then
        assertThat(saved.getId()).isNotNull();
        
        Optional<IngestionRun> retrieved = ingestionRunRepository.findById(saved.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getStatus()).isEqualTo(IngestionRunStatus.RUNNING);
        assertThat(retrieved.get().getProvider()).isEqualTo(MarketProvider.BINANCE);
        assertThat(retrieved.get().getTimeframe()).isEqualTo(Timeframe.D1);
    }

    @Test
    void shouldFindLatestRunning() {
        // Given
        OffsetDateTime t1 = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2);
        OffsetDateTime t2 = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);

        saveRun(t1, IngestionRunStatus.SUCCESS);
        saveRun(t2, IngestionRunStatus.RUNNING);

        // When
        Optional<IngestionRun> running = ingestionRunRepository.findFirstByProviderAndTimeframeAndStatusOrderByStartedAtDesc(
                MarketProvider.BINANCE, Timeframe.D1, IngestionRunStatus.RUNNING
        );

        // Then
        assertThat(running).isPresent();
        assertThat(running.get().getStartedAt().toEpochSecond()).isEqualTo(t2.toEpochSecond());
    }

    @Test
    void shouldFindTopByProviderAndTimeframe() {
        // Given
        OffsetDateTime t1 = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2);
        OffsetDateTime t2 = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);

        saveRun(t1, IngestionRunStatus.FAILED);
        saveRun(t2, IngestionRunStatus.SUCCESS);

        // When
        Optional<IngestionRun> latest = ingestionRunRepository.findTopByProviderAndTimeframeOrderByStartedAtDesc(
                MarketProvider.BINANCE, Timeframe.D1
        );

        // Then
        assertThat(latest).isPresent();
        assertThat(latest.get().getStatus()).isEqualTo(IngestionRunStatus.SUCCESS);
        assertThat(latest.get().getStartedAt().toEpochSecond()).isEqualTo(t2.toEpochSecond());
    }

    private void saveRun(OffsetDateTime startedAt, IngestionRunStatus status) {
        IngestionRun run = new IngestionRun();
        run.setProvider(MarketProvider.BINANCE);
        run.setTimeframe(Timeframe.D1);
        run.setStartedAt(startedAt);
        run.setStatus(status);
        run.setAssetCount(10);
        ingestionRunRepository.save(run);
    }
}
