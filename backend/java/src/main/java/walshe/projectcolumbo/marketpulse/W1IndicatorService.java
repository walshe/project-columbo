package walshe.projectcolumbo.marketpulse;

import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.service.RsiComputationService;
import walshe.projectcolumbo.persistence.service.SignalStateService;
import walshe.projectcolumbo.persistence.service.SuperTrendService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Thin orchestrator that sequences the W1 indicator/signal/breadth pipeline in strict
 * dependency order: SuperTrend W1 -> RSI W1 -> signal detection -> market breadth W1.
 *
 * Phase 3 calls processAllActiveAssets() as the single entry point for the W1 pipeline pass.
 */
@Service
public class W1IndicatorService {

    private static final Logger log = LoggerFactory.getLogger(W1IndicatorService.class);

    private final SuperTrendService superTrendService;
    private final RsiComputationService rsiComputationService;
    private final SignalStateService signalStateService;
    private final MarketPulseService marketPulseService;

    public W1IndicatorService(SuperTrendService superTrendService,
                               RsiComputationService rsiComputationService,
                               SignalStateService signalStateService,
                               MarketPulseService marketPulseService) {
        this.superTrendService = superTrendService;
        this.rsiComputationService = rsiComputationService;
        this.signalStateService = signalStateService;
        this.marketPulseService = marketPulseService;
    }

    /**
     * Run the full W1 indicator/signal/breadth pipeline for all active assets.
     *
     * Step ordering is load-bearing:
     *   1. SuperTrend W1 — indicators must exist before signal detection
     *   2. RSI W1        — indicators must exist before signal detection
     *   3. detectDaily() — covers W1 automatically (iterates Timeframe.values());
     *                      MUST run after both indicators (RESEARCH Pitfall 4)
     *   4. computeForTimeframe(W1) — breadth snapshot requires prior W1 signal states
     *                                (RESEARCH Pitfall 2)
     */
    @Transactional
    public void processAllActiveAssets() {
        log.info("Starting phase: W1_SUPERTREND");
        long superTrendStart = System.currentTimeMillis();
        superTrendService.processAllActiveAssets(Timeframe.W1, 10, new BigDecimal("2.0"), false);
        log.info("Completed phase: W1_SUPERTREND in {}ms", System.currentTimeMillis() - superTrendStart);

        log.info("Starting phase: W1_RSI");
        long rsiStart = System.currentTimeMillis();
        rsiComputationService.computeForActiveAssets(Timeframe.W1, 14, false);
        log.info("Completed phase: W1_RSI in {}ms", System.currentTimeMillis() - rsiStart);

        log.info("Starting phase: W1_SIGNAL");
        long signalStart = System.currentTimeMillis();
        signalStateService.detectDaily();
        log.info("Completed phase: W1_SIGNAL in {}ms", System.currentTimeMillis() - signalStart);

        log.info("Starting phase: W1_MARKET_PULSE");
        long pulseStart = System.currentTimeMillis();
        marketPulseService.computeForTimeframe(Timeframe.W1);
        log.info("Completed phase: W1_MARKET_PULSE in {}ms", System.currentTimeMillis() - pulseStart);
    }
}
