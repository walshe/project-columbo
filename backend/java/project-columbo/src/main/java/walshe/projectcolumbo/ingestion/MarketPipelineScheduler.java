package walshe.projectcolumbo.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import walshe.projectcolumbo.persistence.MarketProvider;
import walshe.projectcolumbo.persistence.Timeframe;

@Component
public class MarketPipelineScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MarketPipelineScheduler.class);

    private final MarketPipelineService marketPipelineService;

    public MarketPipelineScheduler(MarketPipelineService marketPipelineService) {
        this.marketPipelineService = marketPipelineService;
    }

    @Scheduled(cron = "${app.market-pipeline.cron}")
    public void scheduledRun() {
        logger.info("Triggering scheduled market pipeline");
        try {
            marketPipelineService.runDaily(MarketProvider.BINANCE, Timeframe.D1, RunMode.INCREMENTAL);
            logger.info("Scheduled market pipeline completed successfully");
        } catch (IngestionAlreadyRunningException e) {
            logger.info("Scheduled market pipeline skipped - already running: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Scheduled market pipeline failed", e);
        }
    }
}
