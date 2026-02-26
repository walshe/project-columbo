package walshe.projectcolumbo.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import walshe.projectcolumbo.persistence.MarketProvider;
import walshe.projectcolumbo.persistence.Timeframe;

@Component
class MarketPipelineScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketPipelineScheduler.class);

    private final MarketPipelineService pipelineService;

    MarketPipelineScheduler(MarketPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    // Single scheduler for the whole market pipeline
    @Scheduled(cron = "${app.market-pipeline.cron}")
    void runDailyPipeline() {
        try {
            log.info("Scheduled pipeline trigger: starting daily market pipeline");
            pipelineService.runDaily(MarketProvider.BINANCE, Timeframe.D1, RunMode.INCREMENTAL);
        } catch (IngestionAlreadyRunningException e) {
            // Concurrency: skip silently with info log
            log.info("Scheduled pipeline skipped: a RUNNING ingestion exists for {} {}", MarketProvider.BINANCE, Timeframe.D1);
        } catch (Exception e) {
            log.error("Scheduled pipeline failed: {}", e.getMessage(), e);
        }
    }
}
