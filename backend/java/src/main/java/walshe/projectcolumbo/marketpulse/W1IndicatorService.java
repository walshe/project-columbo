package walshe.projectcolumbo.marketpulse;

import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.service.ElderImpulseStateService;
import walshe.projectcolumbo.persistence.service.EmaComputationService;
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
    private final EmaComputationService emaComputationService;
    private final SignalStateService signalStateService;
    private final ElderImpulseStateService elderImpulseStateService;
    private final MarketPulseService marketPulseService;

    public W1IndicatorService(SuperTrendService superTrendService,
                               RsiComputationService rsiComputationService,
                               EmaComputationService emaComputationService,
                               SignalStateService signalStateService,
                               ElderImpulseStateService elderImpulseStateService,
                               MarketPulseService marketPulseService) {
        this.superTrendService = superTrendService;
        this.rsiComputationService = rsiComputationService;
        this.emaComputationService = emaComputationService;
        this.signalStateService = signalStateService;
        this.elderImpulseStateService = elderImpulseStateService;
        this.marketPulseService = marketPulseService;
    }

    /**
     * Run the full W1 indicator/signal/breadth pipeline for all active assets.
     *
     * Step ordering is load-bearing:
     *   1. SuperTrend W1 — indicators must exist before signal detection
     *   2. RSI W1        — indicators must exist before signal detection
     *   3. detectForTimeframe(W1) — scoped to W1 only; avoids spurious D1 re-detection
     *                               that would have occurred with detectDaily() here.
     *                               MUST run after both indicators (RESEARCH Pitfall 4)
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

        // DISABLED: W1_EMA (Elder dependency)
        // log.info("Starting phase: W1_EMA");
        // long emaStart = System.currentTimeMillis();
        // emaComputationService.computeForActiveAssets(Timeframe.W1, 26, false);
        // log.info("Completed phase: W1_EMA in {}ms", System.currentTimeMillis() - emaStart);

        log.info("Starting phase: W1_SIGNAL");
        long signalStart = System.currentTimeMillis();
        signalStateService.detectForTimeframe(Timeframe.W1);
        log.info("Completed phase: W1_SIGNAL in {}ms", System.currentTimeMillis() - signalStart);

        // DISABLED: W1_IMPULSE (Elder Impulse System)
        // log.info("Starting phase: W1_IMPULSE");
        // long impulseStart = System.currentTimeMillis();
        // elderImpulseStateService.computeForAllActiveAssets(Timeframe.W1);
        // log.info("Completed phase: W1_IMPULSE in {}ms", System.currentTimeMillis() - impulseStart);

        log.info("Starting phase: W1_MARKET_PULSE");
        long pulseStart = System.currentTimeMillis();
        marketPulseService.computeForTimeframe(Timeframe.W1);
        log.info("Completed phase: W1_MARKET_PULSE in {}ms", System.currentTimeMillis() - pulseStart);
    }
}
