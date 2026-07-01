package walshe.projectcolumbo.persistence.service;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.entity.Asset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SignalStateService {
    private static final Logger log = LoggerFactory.getLogger(SignalStateService.class);

    private final AssetRepository assetRepository;
    private final SignalStateAssetProcessor assetProcessor;

    public SignalStateService(AssetRepository assetRepository,
                              SignalStateAssetProcessor assetProcessor) {
        this.assetRepository = assetRepository;
        this.assetProcessor = assetProcessor;
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
                    // 3. Process the asset to see if there are any new trend changes.
                    // Delegated to a separate bean so REQUIRES_NEW actually opens a new
                    // transaction/connection per asset instead of riding the ambient one.
                    SignalStateAssetProcessor.ProcessingStats stats = assetProcessor.processAsset(asset, timeframe, false);
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
     * Detect signals for all active assets scoped to a single timeframe.
     * Use this instead of {@link #detectDaily()} when only one timeframe's
     * indicators are available (e.g. Phase 3 D1-only, Phase 6 W1-only).
     */
    public void detectForTimeframe(Timeframe timeframe) {
        List<Asset> activeAssets = assetRepository.findByActiveTrue();
        log.info("Starting SignalState detection for {} active assets (timeframe={})", activeAssets.size(), timeframe);

        int totalInserted = 0;
        int totalUpdated = 0;
        int totalSkipped = 0;

        for (Asset asset : activeAssets) {
            try {
                // Delegated to a separate bean so REQUIRES_NEW actually opens a new
                // transaction/connection per asset instead of riding the ambient one.
                SignalStateAssetProcessor.ProcessingStats stats = assetProcessor.processAsset(asset, timeframe, false);
                totalInserted += stats.inserted;
                totalUpdated += stats.updated;
                totalSkipped += stats.skipped;
            } catch (Exception e) {
                log.error("Failed to detect SignalState for asset {} {}: {}",
                        asset.getSymbol(), timeframe, e.getMessage(), e);
            }
        }

        log.info("SignalState detection completed for timeframe={}. Total: inserted={}, updated={}, skipped={}",
                timeframe, totalInserted, totalUpdated, totalSkipped);
    }
}
