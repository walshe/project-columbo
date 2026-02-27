package walshe.projectcolumbo.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class RsiComputationService {
    private static final Logger log = LoggerFactory.getLogger(RsiComputationService.class);

    private final AssetRepository assetRepository;
    private final CandleRepository candleRepository;
    private final RsiRepository rsiRepository;
    private final RsiCalculator rsiCalculator;

    public RsiComputationService(AssetRepository assetRepository,
                                  CandleRepository candleRepository,
                                  RsiRepository rsiRepository,
                                  RsiCalculator rsiCalculator) {
        this.assetRepository = assetRepository;
        this.candleRepository = candleRepository;
        this.rsiRepository = rsiRepository;
        this.rsiCalculator = rsiCalculator;
    }

    @Transactional
    public void computeForActiveAssets(Timeframe timeframe, int period, boolean fullRecalc) {
        List<Asset> activeAssets = assetRepository.findByActiveTrue();
        log.info("Starting RSI computation for {} active assets on {} timeframe", activeAssets.size(), timeframe);

        for (Asset asset : activeAssets) {
            try {
                this.computeForAsset(asset, timeframe, period, fullRecalc);
            } catch (Exception e) {
                log.error("Failed to compute RSI for asset {}: {}", asset.getSymbol(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void computeForAsset(Asset asset, Timeframe timeframe, int period, boolean fullRecalc) {
        log.debug("Computing RSI for asset: {} [{}]", asset.getSymbol(), timeframe);

        // Fetch all finalized candles for the asset
        List<Candle> allCandles = candleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(asset, timeframe);
        List<Candle> finalizedCandles = CandleFilters.finalizedBeforeUtcMidnightToday(allCandles, OffsetDateTime.now());

        if (finalizedCandles.size() <= period) {
            log.debug("Not enough finalized candles for RSI calculation for {} (need {}, have {})", 
                    asset.getSymbol(), period + 1, finalizedCandles.size());
            return;
        }

        List<RsiCalculator.RsiResult> results;
        if (fullRecalc) {
            results = rsiCalculator.calculate(finalizedCandles, period);
        } else {
            // Incremental: Only calculate what's missing
            Optional<RsiIndicator> latestStored = rsiRepository.findFirstByAssetAndTimeframeOrderByCloseTimeDesc(asset, timeframe);
            if (latestStored.isEmpty()) {
                results = rsiCalculator.calculate(finalizedCandles, period);
            } else {
                // To get correct Wilder's smoothing, we need history.
                // For simplicity and since RSI is fast, we recalculate from all finalized candles
                // but only persist the new ones.
                // In a production app with millions of candles, we would store avgGain/avgLoss state.
                results = rsiCalculator.calculate(finalizedCandles, period);
            }
        }

        ProcessingStats stats = upsertResults(asset, timeframe, results);
        log.info("RSI summary for {}: {} inserted, {} updated, {} skipped",
                asset.getSymbol(), stats.inserted, stats.updated, stats.skipped);
    }

    private ProcessingStats upsertResults(Asset asset, Timeframe timeframe, List<RsiCalculator.RsiResult> results) {
        ProcessingStats stats = new ProcessingStats();
        for (RsiCalculator.RsiResult result : results) {
            Optional<RsiIndicator> existing = rsiRepository.findByAssetAndTimeframeAndCloseTime(
                    asset, timeframe, result.closeTime());

            if (existing.isPresent()) {
                RsiIndicator rsi = existing.get();
                if (rsi.getRsiValue().compareTo(result.rsiValue()) != 0) {
                    rsi.setRsiValue(result.rsiValue());
                    rsiRepository.save(rsi);
                    stats.updated++;
                } else {
                    stats.skipped++;
                }
            } else {
                RsiIndicator rsi = new RsiIndicator();
                rsi.setAsset(asset);
                rsi.setTimeframe(timeframe);
                rsi.setCloseTime(result.closeTime());
                rsi.setRsiValue(result.rsiValue());
                rsiRepository.save(rsi);
                stats.inserted++;
            }
        }
        return stats;
    }

    private static class ProcessingStats {
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
    }
}
