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
    private final CandleRepository candleRepository;
    private final SuperTrendRepository superTrendRepository;
    private final SignalStateRepository signalStateRepository;
    private final SignalStateCalculator calculator;

    public SignalStateService(AssetRepository assetRepository,
                              CandleRepository candleRepository,
                              SuperTrendRepository superTrendRepository,
                              SignalStateRepository signalStateRepository,
                              SignalStateCalculator calculator) {
        this.assetRepository = assetRepository;
        this.candleRepository = candleRepository;
        this.superTrendRepository = superTrendRepository;
        this.signalStateRepository = signalStateRepository;
        this.calculator = calculator;
    }

    /**
     * Entry point for the scheduled daily signal detection.
     * This is triggered by a timer to run automatically.
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

    /**
     * Main process to detect signals for all active assets (like BTC, ETH)
     * across all available timeframes (like Daily).
     */
    @Transactional
    public void detectDaily() {
        // 1. Get all assets that are currently marked as active
        List<Asset> activeAssets = assetRepository.findByActiveTrue();
        log.info("Starting SignalState detection for {} active assets", activeAssets.size());

        int totalInserted = 0;
        int totalUpdated = 0;
        int totalSkipped = 0;

        // 2. Iterate through each asset and each timeframe
        for (Asset asset : activeAssets) {
            for (Timeframe timeframe : Timeframe.values()) {
                try {
                    // 3. Process the asset to see if there are any new trend changes
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

    /**
     * Processes a single asset for a specific timeframe to identify trend signals.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProcessingStats processAsset(Asset asset, Timeframe timeframe, boolean fullRecalc) {
        // We only care about data from completed days (before today's midnight)
        OffsetDateTime boundary = CandleFilters.utcMidnightToday(OffsetDateTime.now());

        // Step 1: Find the last trend state we recorded for this asset
        Optional<SignalState> latestStored = signalStateRepository.findFirstByAssetIdAndTimeframeAndIndicatorTypeOrderByCloseTimeDesc(
                asset.getId(), timeframe, IndicatorType.SUPERTREND);

        List<SuperTrendIndicator> indicatorsToProcess;
        SuperTrendDirection previousDirection = null;

        // Step 2: Decide which data to look at
        if (fullRecalc || latestStored.isEmpty()) {
            // No history or full refresh requested: look at all historical data
            indicatorsToProcess = superTrendRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(asset, timeframe);
        } else {
            // Incremental update: only look at data newer than what we already have
            OffsetDateTime lastCloseTime = latestStored.get().getCloseTime();
            indicatorsToProcess = superTrendRepository.findByAssetAndTimeframeAndCloseTimeAfterOrderByCloseTimeAsc(
                    asset, timeframe, lastCloseTime);

            // To detect a trend "flip" (reversal), we need to know the trend direction of the previous record
            Optional<SuperTrendIndicator> lastStoredIndicator = superTrendRepository.findByAssetAndTimeframeAndCloseTime(
                    asset, timeframe, lastCloseTime);
            if (lastStoredIndicator.isPresent()) {
                previousDirection = lastStoredIndicator.get().getDirection();
            }
        }

        // Step 3: Only process indicators for finalized periods (e.g., yesterday or earlier)
        List<SuperTrendIndicator> finalizedIndicators = indicatorsToProcess.stream()
                .filter(i -> i.getCloseTime().isBefore(boundary))
                .toList();

        // Step 4: Handle cases where there is no new trend data available
        if (finalizedIndicators.isEmpty()) {
            // Even if there's no new signal, we check if there's a finalized candle (price data)
            Optional<Candle> latestFinalizedCandle = candleRepository.findFirstByAssetAndTimeframeAndCloseTimeBeforeOrderByCloseTimeDesc(
                    asset, timeframe, boundary);

            if (latestFinalizedCandle.isPresent()) {
                OffsetDateTime closeTime = latestFinalizedCandle.get().getCloseTime();

                if (latestStored.isEmpty()) {
                    // Case A: This asset is new and hasn't produced a signal yet (usually due to lack of data)
                    // We mark it as UNKNOWN so we know it's being tracked but has no trend yet.
                    SignalState newState = new SignalState(
                            asset,
                            timeframe,
                            IndicatorType.SUPERTREND,
                            closeTime,
                            TrendState.UNKNOWN,
                            SignalEvent.NONE
                    );
                    signalStateRepository.save(newState);
                    return new ProcessingStats(1, 0, 0);
                } else {
                    // Case B: The asset already has a record. 
                    // If it was previously UNKNOWN, we update its time to the latest candle 
                    // so it stays current in our reports even without a trend.
                    SignalState stored = latestStored.get();
                    if (stored.getTrendState() == TrendState.UNKNOWN && stored.getCloseTime().isBefore(closeTime)) {
                        stored.setCloseTime(closeTime);
                        signalStateRepository.save(stored);
                        return new ProcessingStats(0, 1, 0);
                    }
                }
            }
            return new ProcessingStats(0, 0, 0);
        }

        // Step 5: Calculate the trend states (Bullish/Bearish) and events (Reversals)
        List<SignalStateResult> results = calculator.calculate(finalizedIndicators, previousDirection);

        ProcessingStats stats = new ProcessingStats(0, 0, 0);

        // Step 6: Save the results to the database
        for (SignalStateResult result : results) {
            // Check if we already have a record for this specific time
            Optional<SignalState> existing = signalStateRepository.findByAssetAndTimeframeAndIndicatorTypeAndCloseTime(
                    asset, timeframe, IndicatorType.SUPERTREND, result.closeTime());

            if (existing.isPresent()) {
                SignalState state = existing.get();
                // If the data matches, we skip it to avoid unnecessary database work
                if (state.getTrendState() == result.trendState() && state.getEvent() == result.event()) {
                    stats.skipped++;
                } else {
                    // If the trend changed (rare for finalized data), update the existing record
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
                // Save a brand new trend signal
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
