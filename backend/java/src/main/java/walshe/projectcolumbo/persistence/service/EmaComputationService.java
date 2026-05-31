package walshe.projectcolumbo.persistence.service;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.EmaRepository;
import walshe.projectcolumbo.persistence.repository.CandleRepository;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.entity.EmaIndicator;
import walshe.projectcolumbo.persistence.entity.Asset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class EmaComputationService {
    private static final Logger log = LoggerFactory.getLogger(EmaComputationService.class);

    private final AssetRepository assetRepository;
    private final CandleRepository candleRepository;
    private final EmaRepository emaRepository;
    private final EmaCalculator emaCalculator;

    public EmaComputationService(AssetRepository assetRepository,
                                  CandleRepository candleRepository,
                                  EmaRepository emaRepository,
                                  EmaCalculator emaCalculator) {
        this.assetRepository = assetRepository;
        this.candleRepository = candleRepository;
        this.emaRepository = emaRepository;
        this.emaCalculator = emaCalculator;
    }

    @Transactional
    public void computeForActiveAssets(Timeframe timeframe, int period, boolean fullRecalc) {
        List<Asset> activeAssets = assetRepository.findByActiveTrue();
        log.info("Starting EMA({}) computation for {} active assets on {} timeframe", period, activeAssets.size(), timeframe);

        for (Asset asset : activeAssets) {
            try {
                this.computeForAsset(asset, timeframe, period, fullRecalc);
            } catch (Exception e) {
                log.error("Failed to compute EMA({}) for asset {}: {}", period, asset.getSymbol(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void computeForAsset(Asset asset, Timeframe timeframe, int period, boolean fullRecalc) {
        log.debug("Computing EMA({}) for asset: {} [{}]", period, asset.getSymbol(), timeframe);

        OffsetDateTime boundary = CandleFilters.utcMidnightToday(OffsetDateTime.now());

        // Early-exit guard: two cheap single-row lookups before we touch the full candle history.
        // If the latest stored EMA row already covers the latest finalized candle, no new data
        // has arrived since the last pipeline run — skip the expensive full-history load entirely.
        // fullRecalc bypasses this guard intentionally (used for backfill / parameter changes).
        if (!fullRecalc) {
            Optional<EmaIndicator> latestStored = emaRepository
                    .findFirstByAssetAndTimeframeAndPeriodOrderByCloseTimeDesc(asset, timeframe, period);
            if (latestStored.isPresent()) {
                Optional<Candle> latestCandle = candleRepository
                        .findFirstByAssetAndTimeframeAndCloseTimeBeforeOrderByCloseTimeDesc(asset, timeframe, boundary);
                if (latestCandle.isPresent()
                        && latestStored.get().getCloseTime().equals(latestCandle.get().getCloseTime())) {
                    log.debug("EMA({}) already up-to-date for {} [{}] — skipping", period, asset.getSymbol(), timeframe);
                    return;
                }
            }
        }

        // Load all finalized candles. EMA is a recursive formula — each value depends on the
        // one before it, so we must always start from bar 1 to produce the correct seed average.
        // The early-exit guard above ensures we only reach this point when a new candle exists.
        List<Candle> allCandles = candleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(asset, timeframe);
        List<Candle> finalizedCandles = CandleFilters.finalizedBeforeUtcMidnightToday(allCandles, OffsetDateTime.now());

        if (finalizedCandles.size() < period) {
            log.debug("Not enough finalized candles for EMA({}) calculation for {} (need {}, have {})",
                    period, asset.getSymbol(), period, finalizedCandles.size());
            return;
        }

        List<EmaCalculator.EmaResult> results = emaCalculator.calculate(finalizedCandles, period);

        ProcessingStats stats = upsertResults(asset, timeframe, period, results);
        log.info("EMA({}) summary for {}: {} inserted, {} updated, {} skipped",
                period, asset.getSymbol(), stats.inserted, stats.updated, stats.skipped);
    }

    private ProcessingStats upsertResults(Asset asset, Timeframe timeframe, int period, List<EmaCalculator.EmaResult> results) {
        ProcessingStats stats = new ProcessingStats();
        for (EmaCalculator.EmaResult result : results) {
            Optional<EmaIndicator> existing = emaRepository.findByAssetAndTimeframeAndPeriodAndCloseTime(
                    asset, timeframe, period, result.closeTime());

            if (existing.isPresent()) {
                EmaIndicator ema = existing.get();
                if (ema.getEmaValue().compareTo(result.emaValue()) != 0) {
                    ema.setEmaValue(result.emaValue());
                    emaRepository.save(ema);
                    stats.updated++;
                } else {
                    stats.skipped++;
                }
            } else {
                EmaIndicator ema = new EmaIndicator();
                ema.setAsset(asset);
                ema.setTimeframe(timeframe);
                ema.setPeriod(period);
                ema.setCloseTime(result.closeTime());
                ema.setEmaValue(result.emaValue());
                emaRepository.save(ema);
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
