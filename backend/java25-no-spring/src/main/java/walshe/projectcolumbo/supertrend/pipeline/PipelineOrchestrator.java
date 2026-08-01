package walshe.projectcolumbo.supertrend.pipeline;

import walshe.projectcolumbo.supertrend.indicator.IndicatorComputationService;
import walshe.projectcolumbo.supertrend.ingestion.CandleIngestionService;
import walshe.projectcolumbo.supertrend.ingestion.IngestionStats;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.IngestionRunDao;
import walshe.projectcolumbo.supertrend.rollup.CandleRollupService;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Runs the daily pipeline in a strict phase order: ingest -&gt; D1 indicators -&gt; W1 rollup
 * -&gt; W1 indicators. Only work <em>within</em> a phase is parallelized (per-asset, via
 * {@link IndicatorComputationService}) — phases themselves always run in this sequence, and
 * each phase's writes are fully committed before the next phase reads them.
 * <p>
 * D1/W1 signal detection and market pulse phases slot in between "D1 indicators" and "W1
 * rollup", and after "W1 indicators", respectively, once those capabilities exist (see
 * openspec tasks.md groups 6 and 7 — this class intentionally doesn't call them yet).
 */
public final class PipelineOrchestrator {

    private static final Logger LOG = System.getLogger(PipelineOrchestrator.class.getName());

    private final AssetDao assetDao;
    private final IngestionRunDao ingestionRunDao;
    private final CandleIngestionService candleIngestionService;
    private final IndicatorComputationService indicatorComputationService;
    private final CandleRollupService candleRollupService;
    private final Clock clock;

    public PipelineOrchestrator(
            AssetDao assetDao,
            IngestionRunDao ingestionRunDao,
            CandleIngestionService candleIngestionService,
            IndicatorComputationService indicatorComputationService,
            CandleRollupService candleRollupService,
            Clock clock
    ) {
        this.assetDao = assetDao;
        this.ingestionRunDao = ingestionRunDao;
        this.candleIngestionService = candleIngestionService;
        this.indicatorComputationService = indicatorComputationService;
        this.candleRollupService = candleRollupService;
        this.clock = clock;
    }

    public PipelineRunResult runDaily(Provider provider, Timeframe timeframe) {
        if (ingestionRunDao.isRunning(provider, timeframe)) {
            throw new IngestionAlreadyRunningException(provider, timeframe);
        }

        int assetCount = assetDao.findAllActive().size();
        OffsetDateTime startedAt = OffsetDateTime.now(clock);
        long runId = ingestionRunDao.start(provider, timeframe, assetCount, startedAt);
        LOG.log(Level.INFO, "Pipeline run {0} started for {1} {2} ({3} active assets)", runId, provider, timeframe, assetCount);

        IngestionStats ingestionStats;
        try {
            ingestionStats = candleIngestionService.ingestDaily();
            indicatorComputationService.computeForAllActiveAssets(Timeframe.D1);
            candleRollupService.rollupForAllActiveAssets();
            indicatorComputationService.computeForAllActiveAssets(Timeframe.W1);
        } catch (Exception e) {
            // Catches Exception, not just RuntimeException: a run left RUNNING forever because an
            // unexpected checked/wrapped failure slipped past this catch is worse than a broad net.
            LOG.log(Level.ERROR, "Pipeline run " + runId + " failed unexpectedly", e);
            complete(runId, startedAt, IngestionRunStatus.FAILED, new IngestionStats(0, 0, 0, 1, e.getMessage()));
            throw e;
        }

        IngestionRunStatus status = determineStatus(ingestionStats, assetCount);
        complete(runId, startedAt, status, ingestionStats);
        LOG.log(Level.INFO, "Pipeline run {0} finished with status {1}", runId, status);
        return new PipelineRunResult(runId, status);
    }

    private void complete(long runId, OffsetDateTime startedAt, IngestionRunStatus status, IngestionStats stats) {
        OffsetDateTime finishedAt = OffsetDateTime.now(clock);
        long durationMs = Duration.between(startedAt, finishedAt).toMillis();
        ingestionRunDao.complete(runId, new IngestionRunOutcome(
                status,
                finishedAt,
                durationMs,
                stats.insertedCount(),
                stats.updatedCount(),
                stats.unchangedCount(),
                stats.errorCount(),
                stats.firstErrorMessage()
        ));
    }

    private static IngestionRunStatus determineStatus(IngestionStats stats, int assetCount) {
        if (stats.errorCount() == 0) {
            return IngestionRunStatus.SUCCESS;
        }
        return stats.errorCount() >= assetCount ? IngestionRunStatus.FAILED : IngestionRunStatus.PARTIAL;
    }
}
