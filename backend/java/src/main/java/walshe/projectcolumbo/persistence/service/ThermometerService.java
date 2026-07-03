package walshe.projectcolumbo.persistence.service;

import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.entity.ThermometerIndicator;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.CandleRepository;
import walshe.projectcolumbo.persistence.repository.ThermometerRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
/** DISABLED: Market Thermometer is not active — not invoked by the pipeline. Retained (functional) for re-enablement. */
public class ThermometerService {

    private static final Logger log = LoggerFactory.getLogger(ThermometerService.class);

    private final AssetRepository assetRepository;
    private final CandleRepository candleRepository;
    private final ThermometerRepository thermometerRepository;
    private final ThermometerCalculator thermometerCalculator;

    public ThermometerService(AssetRepository assetRepository,
                               CandleRepository candleRepository,
                               ThermometerRepository thermometerRepository,
                               ThermometerCalculator thermometerCalculator) {
        this.assetRepository = assetRepository;
        this.candleRepository = candleRepository;
        this.thermometerRepository = thermometerRepository;
        this.thermometerCalculator = thermometerCalculator;
    }

    @Transactional
    public void computeForActiveAssets(boolean fullRecalc) {
        List<Asset> activeAssets = assetRepository.findByActiveTrue();
        log.info("Starting Thermometer computation for {} active assets", activeAssets.size());
        for (Asset asset : activeAssets) {
            try {
                computeForAsset(asset, fullRecalc);
            } catch (Exception e) {
                log.error("Failed to compute Thermometer for asset {}: {}", asset.getSymbol(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void computeForAsset(Asset asset, boolean fullRecalc) {
        OffsetDateTime boundary = CandleFilters.utcMidnightToday(OffsetDateTime.now());

        // Early-exit guard: one cheap single-row lookup before we load the full candle history.
        // The Thermometer temperature is derived from today's bar vs. yesterday's, so if the
        // latest stored row already covers the latest finalized candle, the result cannot have
        // changed and there is nothing to persist.
        // fullRecalc bypasses this guard intentionally (used for backfill).
        if (!fullRecalc) {
            Optional<ThermometerIndicator> latestStored = thermometerRepository
                    .findFirstByAssetOrderByCloseTimeDesc(asset);
            if (latestStored.isPresent()) {
                Optional<Candle> latestCandle = candleRepository
                        .findFirstByAssetAndTimeframeAndCloseTimeBeforeOrderByCloseTimeDesc(asset, Timeframe.D1, boundary);
                if (latestCandle.isPresent()
                        && latestStored.get().getCloseTime().equals(latestCandle.get().getCloseTime())) {
                    log.debug("Thermometer already up-to-date for {} — skipping", asset.getSymbol());
                    return;
                }
            }
        }

        List<Candle> allCandles = candleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(asset, Timeframe.D1);
        List<Candle> finalizedCandles = CandleFilters.finalizedBeforeUtcMidnightToday(allCandles, OffsetDateTime.now());

        if (finalizedCandles.size() < 2) {
            log.debug("Not enough finalized candles for Thermometer calculation for {} (need 2, have {})",
                    asset.getSymbol(), finalizedCandles.size());
            return;
        }

        List<ThermometerCalculator.ThermometerResult> results =
                thermometerCalculator.calculate(finalizedCandles);

        ProcessingStats stats = upsertResults(asset, results);
        log.info("Thermometer summary for {}: {} inserted, {} updated, {} skipped",
                asset.getSymbol(), stats.inserted(), stats.updated(), stats.skipped());
    }

    private ProcessingStats upsertResults(Asset asset,
                                           List<ThermometerCalculator.ThermometerResult> results) {
        int inserted = 0, updated = 0, skipped = 0;

        for (ThermometerCalculator.ThermometerResult result : results) {
            Optional<ThermometerIndicator> existing =
                    thermometerRepository.findByAssetAndCloseTime(asset, result.closeTime());

            if (existing.isPresent()) {
                ThermometerIndicator row = existing.get();
                boolean tempMatch = row.getTemperature().compareTo(result.temperature()) == 0;
                // Null-safe EMA comparison: both null → match; one null → mismatch; both non-null → compareTo
                boolean emaMatch;
                if (row.getTemperatureEma() == null && result.temperatureEma() == null) {
                    emaMatch = true;
                } else if (row.getTemperatureEma() == null || result.temperatureEma() == null) {
                    emaMatch = false;
                } else {
                    emaMatch = row.getTemperatureEma().compareTo(result.temperatureEma()) == 0;
                }

                if (tempMatch && emaMatch) {
                    skipped++;
                } else {
                    row.setTemperature(result.temperature());
                    row.setTemperatureEma(result.temperatureEma());
                    thermometerRepository.save(row);
                    updated++;
                }
            } else {
                ThermometerIndicator row = new ThermometerIndicator();
                row.setAsset(asset);
                row.setCloseTime(result.closeTime());
                row.setTemperature(result.temperature());
                row.setTemperatureEma(result.temperatureEma());
                thermometerRepository.save(row);
                inserted++;
            }
        }

        return new ProcessingStats(inserted, updated, skipped);
    }

    private record ProcessingStats(int inserted, int updated, int skipped) {}
}
