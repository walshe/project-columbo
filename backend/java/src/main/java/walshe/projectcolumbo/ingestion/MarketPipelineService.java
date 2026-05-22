package walshe.projectcolumbo.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.service.SignalStateService;
import walshe.projectcolumbo.persistence.service.SuperTrendService;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.marketpulse.MarketPulseService;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.service.RsiComputationService;
import walshe.projectcolumbo.rollup.CandleRollupService;
import walshe.projectcolumbo.marketpulse.W1IndicatorService;

import java.time.DayOfWeek;
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
    private final CandleRollupService candleRollupService;
    private final W1IndicatorService w1IndicatorService;

    public MarketPipelineService(CandleIngestionService candleIngestionService,
                                 SuperTrendService superTrendService,
                                 RsiComputationService rsiComputationService,
                                 SignalStateService signalStateService,
                                 MarketPulseService marketPulseService,
                                 IngestionRunRepository ingestionRunRepository,
                                 AssetRepository assetRepository,
                                 IngestionOrchestrator orchestrator,
                                 CandleRollupService candleRollupService,
                                 W1IndicatorService w1IndicatorService) {
        this.candleIngestionService = candleIngestionService;
        this.superTrendService = superTrendService;
        this.rsiComputationService = rsiComputationService;
        this.signalStateService = signalStateService;
        this.marketPulseService = marketPulseService;
        this.ingestionRunRepository = ingestionRunRepository;
        this.assetRepository = assetRepository;
        this.orchestrator = orchestrator;
        this.candleRollupService = candleRollupService;
        this.w1IndicatorService = w1IndicatorService;
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

            // PHASE 5: W1 Rollup
            logger.info("Starting phase: W1_ROLLUP");
            long w1RollupStartTime = System.currentTimeMillis();
            candleRollupService.rollupForAllActiveAssets(Timeframe.D1, Timeframe.W1, DayOfWeek.MONDAY);
            logger.info("Completed phase: W1_ROLLUP in {}ms", System.currentTimeMillis() - w1RollupStartTime);

            // PHASE 6: W1 Indicators/Signals/Pulse
            logger.info("Starting phase: W1_PROCESSING");
            long w1ProcessingStartTime = System.currentTimeMillis();
            w1IndicatorService.processAllActiveAssets();
            logger.info("Completed phase: W1_PROCESSING in {}ms", System.currentTimeMillis() - w1ProcessingStartTime);

            // Success Handling
            finalizeRun(run, stats, null);
            logger.info("Market pipeline completed successfully for {} {} in {}ms", 
                    actualProvider, actualTimeframe, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            logger.error("Market pipeline failed during execution: {}", e.getMessage(), e);
            try {
                finalizeRun(run, null, e);
            } catch (Exception finalizationError) {
                logger.error("Failed to finalize run after pipeline error; forcing FAILED status", finalizationError);
                run.setStatus(IngestionRunStatus.FAILED);
                run.setFinishedAt(OffsetDateTime.now());
                String msg = e.getMessage();
                run.setErrorSample(msg != null && msg.length() > 1000 ? msg.substring(0, 1000) : msg);
            }
        } finally {
            run = ingestionRunRepository.save(run);
        }
        return run;
    }

    private void finalizeRun(IngestionRun run, CandleIngestionService.IngestionStats stats, Exception error) {
        orchestrator.finalizeRun(run, stats, error);
    }
}
