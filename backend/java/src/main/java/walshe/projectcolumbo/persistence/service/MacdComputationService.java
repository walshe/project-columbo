package walshe.projectcolumbo.persistence.service;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.MacdRepository;
import walshe.projectcolumbo.persistence.repository.CandleRepository;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.entity.MacdIndicator;
import walshe.projectcolumbo.persistence.entity.Asset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class MacdComputationService {
    private static final Logger log = LoggerFactory.getLogger(MacdComputationService.class);

    private final AssetRepository assetRepository;
    private final CandleRepository candleRepository;
    private final MacdRepository macdRepository;
    private final MacdCalculator macdCalculator;

    public MacdComputationService(AssetRepository assetRepository,
                                   CandleRepository candleRepository,
                                   MacdRepository macdRepository,
                                   MacdCalculator macdCalculator) {
        this.assetRepository = assetRepository;
        this.candleRepository = candleRepository;
        this.macdRepository = macdRepository;
        this.macdCalculator = macdCalculator;
    }

    @Transactional
    public void computeForActiveAssets(Timeframe timeframe, boolean fullRecalc) {
        List<Asset> activeAssets = assetRepository.findByActiveTrue();
        log.info("Starting MACD(12-26-9) computation for {} active assets on {} timeframe", activeAssets.size(), timeframe);

        for (Asset asset : activeAssets) {
            try {
                this.computeForAsset(asset, timeframe, fullRecalc);
            } catch (Exception e) {
                log.error("Failed to compute MACD for asset {}: {}", asset.getSymbol(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void computeForAsset(Asset asset, Timeframe timeframe, boolean fullRecalc) {
        log.debug("Computing MACD(12-26-9) for asset: {} [{}]", asset.getSymbol(), timeframe);

        OffsetDateTime boundary = CandleFilters.utcMidnightToday(OffsetDateTime.now());

        // Early-exit guard: two cheap single-row lookups before we touch the full candle history.
        // MACD needs 34 bars of warm-up (26 slow EMA + 9 signal), so the full-history load is
        // especially expensive.  If the latest stored row already matches the latest finalized
        // candle there is nothing new to compute.
        // fullRecalc bypasses this guard intentionally (used for backfill / parameter changes).
        if (!fullRecalc) {
            Optional<MacdIndicator> latestStored = macdRepository
                    .findFirstByAssetAndTimeframeOrderByCloseTimeDesc(asset, timeframe);
            if (latestStored.isPresent()) {
                Optional<Candle> latestCandle = candleRepository
                        .findFirstByAssetAndTimeframeAndCloseTimeBeforeOrderByCloseTimeDesc(asset, timeframe, boundary);
                if (latestCandle.isPresent()
                        && latestStored.get().getCloseTime().equals(latestCandle.get().getCloseTime())) {
                    log.debug("MACD(12-26-9) already up-to-date for {} [{}] — skipping", asset.getSymbol(), timeframe);
                    return;
                }
            }
        }

        // Load all finalized candles. MACD's slow EMA and signal line are both recursive —
        // the full history is required to produce correct values from bar 1.
        // The early-exit guard above ensures we only reach this point when a new candle exists.
        List<Candle> allCandles = candleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(asset, timeframe);
        List<Candle> finalizedCandles = CandleFilters.finalizedBeforeUtcMidnightToday(allCandles, OffsetDateTime.now());

        int minCandles = MacdCalculator.SLOW_PERIOD + MacdCalculator.SIGNAL_PERIOD - 1;
        if (finalizedCandles.size() < minCandles) {
            log.debug("Not enough finalized candles for MACD calculation for {} (need 34, have {})",
                    asset.getSymbol(), finalizedCandles.size());
            return;
        }

        List<MacdCalculator.MacdResult> results = macdCalculator.calculate(finalizedCandles);

        ProcessingStats stats = upsertResults(asset, timeframe, results);
        log.info("MACD(12-26-9) summary for {}: {} inserted, {} updated, {} skipped",
                asset.getSymbol(), stats.inserted, stats.updated, stats.skipped);
    }

    private ProcessingStats upsertResults(Asset asset, Timeframe timeframe, List<MacdCalculator.MacdResult> results) {
        ProcessingStats stats = new ProcessingStats();
        for (MacdCalculator.MacdResult result : results) {
            Optional<MacdIndicator> existing = macdRepository.findByAssetAndTimeframeAndCloseTime(
                    asset, timeframe, result.closeTime());

            if (existing.isPresent()) {
                MacdIndicator macd = existing.get();
                boolean changed = macd.getMacdLine().compareTo(result.macdLine()) != 0
                        || macd.getSignalLine().compareTo(result.signalLine()) != 0
                        || macd.getHistogram().compareTo(result.histogram()) != 0;
                if (changed) {
                    macd.setMacdLine(result.macdLine());
                    macd.setSignalLine(result.signalLine());
                    macd.setHistogram(result.histogram());
                    macdRepository.save(macd);
                    stats.updated++;
                } else {
                    stats.skipped++;
                }
            } else {
                MacdIndicator macd = new MacdIndicator();
                macd.setAsset(asset);
                macd.setTimeframe(timeframe);
                macd.setCloseTime(result.closeTime());
                macd.setMacdLine(result.macdLine());
                macd.setSignalLine(result.signalLine());
                macd.setHistogram(result.histogram());
                macdRepository.save(macd);
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
