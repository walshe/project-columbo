package walshe.projectcolumbo.persistence.service;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.RsiRepository;
import walshe.projectcolumbo.persistence.repository.CandleRepository;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.entity.RsiIndicator;
import walshe.projectcolumbo.persistence.entity.Asset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Async;
import walshe.projectcolumbo.annotation.ParallelAssetComputation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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

        OffsetDateTime boundary = CandleFilters.utcMidnightToday(OffsetDateTime.now());

        // Early-exit guard: two cheap single-row lookups before we touch the full candle history.
        // RSI uses Wilder's smoothing (a form of EMA), so the full candle chain is needed every
        // time there IS a new bar.  But when there is no new bar, we can skip entirely.
        // fullRecalc bypasses this guard intentionally (used for backfill / parameter changes).
        if (!fullRecalc) {
            Optional<RsiIndicator> latestStored = rsiRepository
                    .findFirstByAssetAndTimeframeOrderByCloseTimeDesc(asset, timeframe);
            if (latestStored.isPresent()) {
                Optional<Candle> latestCandle = candleRepository
                        .findFirstByAssetAndTimeframeAndCloseTimeBeforeOrderByCloseTimeDesc(asset, timeframe, boundary);
                if (latestCandle.isPresent()
                        && latestStored.get().getCloseTime().equals(latestCandle.get().getCloseTime())) {
                    log.debug("RSI already up-to-date for {} [{}] — skipping", asset.getSymbol(), timeframe);
                    return;
                }
            }
        }

        // Load all finalized candles. Wilder's RSI smoothing is path-dependent — it must be
        // seeded from the first bar and replayed forward, so we always need the full history.
        // The early-exit guard above ensures we only reach this point when a new candle exists.
        List<Candle> allCandles = candleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(asset, timeframe);
        List<Candle> finalizedCandles = CandleFilters.finalizedBeforeUtcMidnightToday(allCandles, OffsetDateTime.now());

        if (finalizedCandles.size() <= period) {
            log.debug("Not enough finalized candles for RSI calculation for {} (need {}, have {})",
                    asset.getSymbol(), period + 1, finalizedCandles.size());
            return;
        }

        List<RsiCalculator.RsiResult> results = rsiCalculator.calculate(finalizedCandles, period);

        ProcessingStats stats = upsertResults(asset, timeframe, results);
        log.info("RSI summary for {}: {} inserted, {} updated, {} skipped",
                asset.getSymbol(), stats.inserted, stats.updated, stats.skipped);
    }

    @Async("indicatorComputationExecutor")
    @Transactional
    @ParallelAssetComputation
    public CompletableFuture<Void> computeForAssetAsync(Asset asset, Timeframe timeframe, int period, boolean fullRecalc) {
        try {
            computeForAsset(asset, timeframe, period, fullRecalc);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Async RSI computation failed for asset {}: {}", asset.getSymbol(), e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
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
