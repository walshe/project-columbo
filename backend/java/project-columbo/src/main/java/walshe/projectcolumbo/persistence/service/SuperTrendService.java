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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

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
    public void processAllActiveAssets(Timeframe timeframe, int atrLength, java.math.BigDecimal multiplier, boolean fullRecalc) {
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
