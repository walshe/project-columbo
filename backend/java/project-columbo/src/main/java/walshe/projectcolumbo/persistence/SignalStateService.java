package walshe.projectcolumbo.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class SignalStateService {
    private static final Logger log = LoggerFactory.getLogger(SignalStateService.class);

    private final AssetRepository assetRepository;
    private final SuperTrendRepository superTrendRepository;
    private final SignalStateRepository signalStateRepository;
    private final SignalStateCalculator calculator;

    public SignalStateService(AssetRepository assetRepository,
                              SuperTrendRepository superTrendRepository,
                              SignalStateRepository signalStateRepository,
                              SignalStateCalculator calculator) {
        this.assetRepository = assetRepository;
        this.superTrendRepository = superTrendRepository;
        this.signalStateRepository = signalStateRepository;
        this.calculator = calculator;
    }

    /**
     * Phase 8: Scheduled daily detection.
     */
    public void scheduledDetectDaily() {
        try {
            log.info("Scheduled SignalState detection started");
            detectDaily();
            log.info("Scheduled SignalState detection completed");
        } catch (Exception e) {
            log.error("Scheduled SignalState detection failed: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void detectDaily() {
        List<Asset> activeAssets = assetRepository.findByActiveTrue();
        log.info("Starting SignalState detection for {} active assets", activeAssets.size());

        int totalInserted = 0;
        int totalUpdated = 0;
        int totalSkipped = 0;

        for (Asset asset : activeAssets) {
            for (Timeframe timeframe : Timeframe.values()) {
                try {
                    ProcessingStats stats = this.processAsset(asset, timeframe, false);
                    totalInserted += stats.inserted;
                    totalUpdated += stats.updated;
                    totalSkipped += stats.skipped;
                } catch (Exception e) {
                    log.error("Failed to detect SignalState for asset {} {}: {}",
                            asset.getSymbol(), timeframe, e.getMessage(), e);
                }
            }
        }

        log.info("SignalState detection completed. Total: inserted={}, updated={}, skipped={}",
                totalInserted, totalUpdated, totalSkipped);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProcessingStats processAsset(Asset asset, Timeframe timeframe, boolean fullRecalc) {
        OffsetDateTime boundary = CandleFilters.utcMidnightToday(OffsetDateTime.now());

        Optional<SignalState> latestStored = signalStateRepository.findFirstByAssetIdAndTimeframeAndIndicatorTypeOrderByCloseTimeDesc(
                asset.getId(), timeframe, IndicatorType.SUPERTREND);

        List<SuperTrendIndicator> indicatorsToProcess;
        SuperTrendDirection previousDirection = null;

        if (fullRecalc || latestStored.isEmpty()) {
            indicatorsToProcess = superTrendRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(asset, timeframe);
        } else {
            OffsetDateTime lastCloseTime = latestStored.get().getCloseTime();
            indicatorsToProcess = superTrendRepository.findByAssetAndTimeframeAndCloseTimeAfterOrderByCloseTimeAsc(
                    asset, timeframe, lastCloseTime);

            // To determine reversal for the first new row, we need the direction of the row that produced the last stored state
            Optional<SuperTrendIndicator> lastStoredIndicator = superTrendRepository.findByAssetAndTimeframeAndCloseTime(
                    asset, timeframe, lastCloseTime);
            if (lastStoredIndicator.isPresent()) {
                previousDirection = lastStoredIndicator.get().getDirection();
            }
        }

        // Phase 4: Filter out non-finalized rows
        List<SuperTrendIndicator> finalizedIndicators = indicatorsToProcess.stream()
                .filter(i -> i.getCloseTime().isBefore(boundary))
                .toList();

        if (finalizedIndicators.isEmpty()) {
            return new ProcessingStats(0, 0, 0);
        }

        List<SignalStateResult> results = calculator.calculate(finalizedIndicators, previousDirection);

        ProcessingStats stats = new ProcessingStats(0, 0, 0);

        for (SignalStateResult result : results) {
            Optional<SignalState> existing = signalStateRepository.findByAssetAndTimeframeAndIndicatorTypeAndCloseTime(
                    asset, timeframe, IndicatorType.SUPERTREND, result.closeTime());

            if (existing.isPresent()) {
                SignalState state = existing.get();
                if (state.getTrendState() == result.trendState() && state.getEvent() == result.event()) {
                    stats.skipped++;
                } else {
                    log.warn("REVISION: SignalState changed for {} {} at {}. Old: [{} {}], New: [{} {}]",
                            asset.getSymbol(), timeframe, result.closeTime(),
                            state.getTrendState(), state.getEvent(),
                            result.trendState(), result.event());
                    state.setTrendState(result.trendState());
                    state.setEvent(result.event());
                    signalStateRepository.save(state);
                    stats.updated++;
                }
            } else {
                SignalState newState = new SignalState(
                        asset,
                        timeframe,
                        IndicatorType.SUPERTREND,
                        result.closeTime(),
                        result.trendState(),
                        result.event()
                );
                signalStateRepository.save(newState);
                stats.inserted++;
            }
        }

        log.info("SignalState summary for {} {}: inserted={}, updated={}, skipped={}",
                asset.getSymbol(), timeframe, stats.inserted, stats.updated, stats.skipped);
        return stats;
    }

    public static class ProcessingStats {
        public int inserted;
        public int updated;
        public int skipped;

        public ProcessingStats(int inserted, int updated, int skipped) {
            this.inserted = inserted;
            this.updated = updated;
            this.skipped = skipped;
        }
    }
}
