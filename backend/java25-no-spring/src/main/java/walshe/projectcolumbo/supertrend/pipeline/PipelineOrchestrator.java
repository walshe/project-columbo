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
        RunHandle handle = start(provider, timeframe);
        return executePhases(handle);
    }

    /**
     * Starts a run (synchronously - so a concurrent-run 409 and the new run's id are both known
     * immediately) and runs its phases on a virtual thread rather than the caller's thread, for an
     * HTTP trigger that must return before the pipeline finishes. Failures during the background
     * phases are recorded on the run the same way {@link #runDaily} records them; there's nothing
     * further to propagate to a caller who has already received their response.
     */
    public long triggerAsync(Provider provider, Timeframe timeframe) {
        RunHandle handle = start(provider, timeframe);
        Thread.ofVirtual().name("ingestion-trigger-" + handle.runId()).start(() -> {
            try {
                executePhases(handle);
            } catch (Exception e) {
                // executePhases already logged this and recorded the run FAILED before rethrowing
                // (for runDaily's synchronous caller to see) - on this background thread there is no
                // caller, so letting it propagate would only print a second, redundant stack trace
                // to the default uncaught-exception handler for the same already-handled failure.
            }
        });
        return handle.runId();
    }

    private RunHandle start(Provider provider, Timeframe timeframe) {
        if (ingestionRunDao.isRunning(provider, timeframe)) {
            throw new IngestionAlreadyRunningException(provider, timeframe);
        }

        int assetCount = assetDao.findAllActive().size();
        OffsetDateTime startedAt = OffsetDateTime.now(clock);
        long runId = ingestionRunDao.start(provider, timeframe, assetCount, startedAt);
        LOG.log(Level.INFO, "Pipeline run {0} started for {1} {2} ({3} active assets)", runId, provider, timeframe, assetCount);
        return new RunHandle(runId, assetCount, startedAt);
    }

    private PipelineRunResult executePhases(RunHandle handle) {
        long runId = handle.runId();
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
            complete(handle, IngestionRunStatus.FAILED, new IngestionStats(0, 0, 0, 1, e.getMessage()));
            throw e;
        }

        IngestionRunStatus status = determineStatus(ingestionStats, handle.assetCount());
        complete(handle, status, ingestionStats);
        LOG.log(Level.INFO, "Pipeline run {0} finished with status {1}", runId, status);
        return new PipelineRunResult(runId, status);
    }

    private record RunHandle(long runId, int assetCount, OffsetDateTime startedAt) {
    }

    private void complete(RunHandle handle, IngestionRunStatus status, IngestionStats stats) {
        OffsetDateTime finishedAt = OffsetDateTime.now(clock);
        long durationMs = Duration.between(handle.startedAt(), finishedAt).toMillis();
        ingestionRunDao.complete(handle.runId(), new IngestionRunOutcome(
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
