package walshe.projectcolumbo.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import walshe.projectcolumbo.persistence.AssetRepository;
import walshe.projectcolumbo.persistence.MarketProvider;
import walshe.projectcolumbo.persistence.Timeframe;

import java.time.Duration;
import java.time.OffsetDateTime;

@Component
public class IngestionOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(IngestionOrchestrator.class);

    private final CandleIngestionService candleIngestionService;
    private final IngestionRunRepository ingestionRunRepository;
    private final AssetRepository assetRepository;

    public IngestionOrchestrator(CandleIngestionService candleIngestionService,
                                 IngestionRunRepository ingestionRunRepository,
                                 AssetRepository assetRepository) {
        this.candleIngestionService = candleIngestionService;
        this.ingestionRunRepository = ingestionRunRepository;
        this.assetRepository = assetRepository;
    }

    public IngestionRun runInternal(MarketProvider provider, Timeframe timeframe) {
        // 1. Concurrency check
        ingestionRunRepository.findFirstByProviderAndTimeframeAndStatusOrderByStartedAtDesc(
                provider, timeframe, IngestionRunStatus.RUNNING)
                .ifPresent(run -> {
                    throw new IngestionAlreadyRunningException("Ingestion already running for " + provider + " " + timeframe);
                });

        // 2. Create RUNNING row
        IngestionRun run = new IngestionRun();
        run.setProvider(provider);
        run.setTimeframe(timeframe);
        run.setStartedAt(OffsetDateTime.now());
        run.setStatus(IngestionRunStatus.RUNNING);
        run.setAssetCount((int) assetRepository.count()); // Simple count of all assets
        
        run = ingestionRunRepository.save(run);
        logger.info("Started ingestion run {} for {} {}", run.getId(), provider, timeframe);

        final Long runId = run.getId();
        try {
            // 3. Call CandleIngestionService
            CandleIngestionService.IngestionStats stats = candleIngestionService.ingestDaily();

            // 4. Finalize row
            finalizeRun(run, stats, null);
        } catch (Exception e) {
            logger.error("Ingestion run {} failed with error", runId, e);
            finalizeRun(run, null, e);
        }

        return ingestionRunRepository.save(run);
    }

    private void finalizeRun(IngestionRun run, CandleIngestionService.IngestionStats stats, Exception error) {
        run.setFinishedAt(OffsetDateTime.now());
        run.setDurationMs(Duration.between(run.getStartedAt(), run.getFinishedAt()).toMillis());

        if (stats != null) {
            run.setInsertedCount(stats.insertedCount);
            run.setUpdatedCount(stats.updatedCount);
            run.setSkippedCount(stats.skippedCount);
            run.setErrorCount(stats.errorCount);
            if (stats.firstErrorMessage != null) {
                run.setErrorSample(truncate(stats.firstErrorMessage, 1000));
            }
        }

        if (error != null) {
            run.setStatus(IngestionRunStatus.FAILED);
            if (run.getErrorSample() == null) {
                run.setErrorSample(truncate(error.getMessage(), 1000));
            }
        } else if (stats != null) {
            run.setStatus(deriveStatus(stats));
        }

        logger.info("Finalized ingestion run {} with status {} in {}ms", 
                run.getId(), run.getStatus(), run.getDurationMs());
    }

    private IngestionRunStatus deriveStatus(CandleIngestionService.IngestionStats stats) {
        if (stats.errorCount == 0) {
            return IngestionRunStatus.SUCCESS;
        } else if (stats.insertedCount > 0 || stats.updatedCount > 0 || stats.skippedCount > 0) {
            return IngestionRunStatus.PARTIAL;
        } else {
            return IngestionRunStatus.FAILED;
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
