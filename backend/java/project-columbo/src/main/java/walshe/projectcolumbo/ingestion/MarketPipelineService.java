package walshe.projectcolumbo.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import walshe.projectcolumbo.persistence.AssetRepository;
import walshe.projectcolumbo.persistence.MarketProvider;
import walshe.projectcolumbo.persistence.SignalStateService;
import walshe.projectcolumbo.persistence.SuperTrendService;
import walshe.projectcolumbo.persistence.Timeframe;
import walshe.projectcolumbo.persistence.MarketPulseService;
import walshe.projectcolumbo.persistence.IndicatorType;
import walshe.projectcolumbo.persistence.RsiComputationService;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.math.BigDecimal;

@Service
public class MarketPipelineService {

    private static final Logger logger = LoggerFactory.getLogger(MarketPipelineService.class);

    private final CandleIngestionService candleIngestionService;
    private final SuperTrendService superTrendService;
    private final RsiComputationService rsiComputationService;
    private final SignalStateService signalStateService;
    private final MarketPulseService marketPulseService;
    private final IngestionRunRepository ingestionRunRepository;
    private final AssetRepository assetRepository;
    private final IngestionOrchestrator orchestrator;

    public MarketPipelineService(CandleIngestionService candleIngestionService,
                                 SuperTrendService superTrendService,
                                 RsiComputationService rsiComputationService,
                                 SignalStateService signalStateService,
                                 MarketPulseService marketPulseService,
                                 IngestionRunRepository ingestionRunRepository,
                                 AssetRepository assetRepository,
                                 IngestionOrchestrator orchestrator) {
        this.candleIngestionService = candleIngestionService;
        this.superTrendService = superTrendService;
        this.rsiComputationService = rsiComputationService;
        this.signalStateService = signalStateService;
        this.marketPulseService = marketPulseService;
        this.ingestionRunRepository = ingestionRunRepository;
        this.assetRepository = assetRepository;
        this.orchestrator = orchestrator;
    }

    public IngestionRun runDaily(MarketProvider provider, Timeframe timeframe, RunMode mode) {
        // Defaults
        MarketProvider actualProvider = provider != null ? provider : MarketProvider.BINANCE;
        Timeframe actualTimeframe = timeframe != null ? timeframe : Timeframe.D1;
        // mode is currently always INCREMENTAL in logic, but we can accept it

        logger.info("Starting daily market pipeline for {} {}", actualProvider, actualTimeframe);

        // 1. Concurrency Check
        ingestionRunRepository.findFirstByProviderAndTimeframeAndStatusOrderByStartedAtDesc(
                actualProvider, actualTimeframe, IngestionRunStatus.RUNNING)
                .ifPresent(run -> {
                    logger.info("Concurrency skip: Market pipeline already running for {} {}", actualProvider, actualTimeframe);
                    throw new IngestionAlreadyRunningException("Market pipeline already running for " + actualProvider + " " + actualTimeframe);
                });

        // 2. Create RUNNING record
        IngestionRun run = new IngestionRun();
        run.setProvider(actualProvider);
        run.setTimeframe(actualTimeframe);
        run.setStartedAt(OffsetDateTime.now());
        run.setStatus(IngestionRunStatus.RUNNING);
        run.setAssetCount((int) assetRepository.countByActiveTrue());
        run = ingestionRunRepository.save(run);

        try {
            // PHASE 1: Ingestion
            logger.info("Starting phase: INGESTION");
            long startTime = System.currentTimeMillis();
            CandleIngestionService.IngestionStats stats = candleIngestionService.ingestDaily();
            logger.info("Completed phase: INGESTION in {}ms", System.currentTimeMillis() - startTime);

            // PHASE 2: Indicator Computation
            logger.info("Starting phase: INDICATOR");
            long indicatorStartTime = System.currentTimeMillis();
            // Using default parameters for SuperTrend (10, 2.0)
            superTrendService.processAllActiveAssets(actualTimeframe, 10, new BigDecimal("2.0"), false);
            // Using default parameter for RSI (14)
            rsiComputationService.computeForActiveAssets(actualTimeframe, 14, false);
            logger.info("Completed phase: INDICATOR in {}ms", System.currentTimeMillis() - indicatorStartTime);

            // PHASE 3: Signal Detection
            logger.info("Starting phase: SIGNAL");
            long signalStartTime = System.currentTimeMillis();
            signalStateService.detectDaily();
            logger.info("Completed phase: SIGNAL in {}ms", System.currentTimeMillis() - signalStartTime);

            // PHASE 4: Market Pulse Aggregation
            logger.info("Starting phase: MARKET_PULSE");
            long pulseStartTime = System.currentTimeMillis();
            marketPulseService.computeDaily();
            logger.info("Completed phase: MARKET_PULSE in {}ms", System.currentTimeMillis() - pulseStartTime);

            // Success Handling
            finalizeRun(run, stats, null);
            logger.info("Market pipeline completed successfully for {} {} in {}ms", 
                    actualProvider, actualTimeframe, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            logger.error("Market pipeline failed at phase during execution: {}", e.getMessage());
            finalizeRun(run, null, e);
        } finally {
            run = ingestionRunRepository.save(run);
        }
        return run;
    }

    private void finalizeRun(IngestionRun run, CandleIngestionService.IngestionStats stats, Exception error) {
        orchestrator.finalizeRun(run, stats, error);
    }
}
