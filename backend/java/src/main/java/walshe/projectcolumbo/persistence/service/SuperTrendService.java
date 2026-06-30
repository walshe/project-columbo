package walshe.projectcolumbo.persistence.service;
import walshe.projectcolumbo.persistence.model.SuperTrendResult;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.SuperTrendRepository;
import walshe.projectcolumbo.persistence.repository.CandleRepository;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.entity.SuperTrendIndicator;
import walshe.projectcolumbo.persistence.entity.Asset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Async;
import walshe.projectcolumbo.annotation.ParallelAssetComputation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
public class SuperTrendService {
    private static final Logger log = LoggerFactory.getLogger(SuperTrendService.class);

    private final AssetRepository assetRepository;
    private final CandleRepository candleRepository;
    private final SuperTrendRepository superTrendRepository;
    private final SuperTrendCalculator calculator;

    public SuperTrendService(AssetRepository assetRepository,
                             CandleRepository candleRepository,
                             SuperTrendRepository superTrendRepository,
                             SuperTrendCalculator calculator) {
        this.assetRepository = assetRepository;
        this.candleRepository = candleRepository;
        this.superTrendRepository = superTrendRepository;
        this.calculator = calculator;
    }

    @Transactional
    public synchronized void processAllActiveAssets(Timeframe timeframe, int atrLength, java.math.BigDecimal multiplier, boolean fullRecalc) {
        List<Asset> activeAssets = assetRepository.findByActiveTrue();
        log.info("Starting SuperTrend processing for {} active assets on {} timeframe", activeAssets.size(), timeframe);

        for (Asset asset : activeAssets) {
            try {
                this.processAsset(asset, timeframe, atrLength, multiplier, fullRecalc);
            } catch (Exception e) {
                log.error("Failed to process SuperTrend for asset {}: {}", asset.getSymbol(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void processAsset(Asset asset, Timeframe timeframe, int atrLength, java.math.BigDecimal multiplier, boolean fullRecalc) {
        Optional<SuperTrendIndicator> latestStored = superTrendRepository.findFirstByAssetAndTimeframeOrderByCloseTimeDesc(asset, timeframe);
        OffsetDateTime lastStoredCloseTime = latestStored.map(SuperTrendIndicator::getCloseTime).orElse(null);

        // Early-exit guard: if the most recently stored SuperTrend row already matches the latest
        // finalized candle, the pipeline ran with no new data since last time — nothing to do.
        // SuperTrend's ATR and direction are path-dependent, so when there IS a new candle the
        // full-history calculateIncremental call below is still required.
        // fullRecalc bypasses this guard intentionally (used for backfill / parameter changes).
        if (!fullRecalc && lastStoredCloseTime != null) {
            OffsetDateTime boundary = CandleFilters.utcMidnightToday(OffsetDateTime.now());
            Optional<Candle> latestCandle = candleRepository
                    .findFirstByAssetAndTimeframeAndCloseTimeBeforeOrderByCloseTimeDesc(asset, timeframe, boundary);
            if (latestCandle.isPresent() && lastStoredCloseTime.equals(latestCandle.get().getCloseTime())) {
                log.debug("SuperTrend already up-to-date for {} [{}] — skipping", asset.getSymbol(), timeframe);
                return;
            }
        }

        List<Candle> allCandles = candleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(asset, timeframe);
        List<Candle> finalizedCandles = CandleFilters.finalizedBeforeUtcMidnightToday(allCandles, OffsetDateTime.now());

        if (finalizedCandles.isEmpty()) {
            log.info("No finalized candles for asset {}", asset.getSymbol());
            return;
        }

        List<SuperTrendResult> results = calculator.calculateIncremental(
                finalizedCandles,
                atrLength,
                multiplier,
                lastStoredCloseTime,
                fullRecalc
        );

        ProcessingStats stats = upsertResults(asset, timeframe, results);
        log.info("SuperTrend summary for {}: {} inserted, {} updated, {} skipped",
                asset.getSymbol(), stats.insertedCount, stats.updatedCount, stats.skippedCount);
    }

    @Async("indicatorComputationExecutor")
    @Transactional
    @ParallelAssetComputation
    public CompletableFuture<Void> processAssetAsync(Asset asset, Timeframe timeframe, int atrLength, java.math.BigDecimal multiplier, boolean fullRecalc) {
        try {
            processAsset(asset, timeframe, atrLength, multiplier, fullRecalc);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Async SuperTrend processing failed for asset {}: {}", asset.getSymbol(), e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private ProcessingStats upsertResults(Asset asset, Timeframe timeframe, List<SuperTrendResult> results) {
        ProcessingStats stats = new ProcessingStats();
        for (SuperTrendResult result : results) {
            if (result == null) {
                continue;
            }
            Optional<SuperTrendIndicator> existingOpt = superTrendRepository.findByAssetAndTimeframeAndCloseTime(asset, timeframe, result.closeTime());

            if (existingOpt.isEmpty()) {
                SuperTrendIndicator newItem = SuperTrendIndicator.fromResult(asset, timeframe, result);
                superTrendRepository.save(newItem);
                stats.insertedCount++;
            } else {
                SuperTrendIndicator existing = existingOpt.get();
                if (existing.isSameValues(result)) {
                    stats.skippedCount++;
                } else {
                    log.warn("Revision detected for {} {} at {}. Updating values.",
                            asset.getSymbol(), timeframe, result.closeTime());
                    existing.updateFrom(result);
                    superTrendRepository.save(existing);
                    stats.updatedCount++;
                }
            }
        }
        return stats;
    }

    private static class ProcessingStats {
        int insertedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
    }
}
