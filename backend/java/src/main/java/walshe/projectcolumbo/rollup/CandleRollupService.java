package walshe.projectcolumbo.rollup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.CandleRepository;
import walshe.projectcolumbo.persistence.service.CandleFilters;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class CandleRollupService {

    private static final Logger log = LoggerFactory.getLogger(CandleRollupService.class);

    private final AssetRepository assetRepository;
    private final CandleRepository candleRepository;

    public CandleRollupService(AssetRepository assetRepository, CandleRepository candleRepository) {
        this.assetRepository = assetRepository;
        this.candleRepository = candleRepository;
    }

    @Transactional
    public void rollupForAllActiveAssets(Timeframe sourceTimeframe, Timeframe targetTimeframe, DayOfWeek weekStartDay) {
        List<Asset> activeAssets = assetRepository.findByActiveTrue();
        log.info("Starting candle rollup for {} active assets: {} -> {}", activeAssets.size(), sourceTimeframe, targetTimeframe);

        for (Asset asset : activeAssets) {
            try {
                rollupForAsset(asset, sourceTimeframe, targetTimeframe, weekStartDay);
            } catch (Exception e) {
                log.error("Failed to rollup candles for asset {}: {}", asset.getSymbol(), e.getMessage(), e);
            }
        }
    }

    private void rollupForAsset(Asset asset, Timeframe sourceTimeframe, Timeframe targetTimeframe, DayOfWeek weekStartDay) {
        // Step 1: Fetch source candles sorted ascending
        List<Candle> sourceCandles = candleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(asset, sourceTimeframe);
        if (sourceCandles.isEmpty()) {
            log.info("No source candles for asset {} at timeframe {}", asset.getSymbol(), sourceTimeframe);
            return;
        }

        // Step 2: Determine the last stored target close time
        Optional<OffsetDateTime> lastStoredCloseTime = candleRepository
                .findLatestCloseTime(asset.getId(), targetTimeframe.name())
                .map(obj -> {
                    if (obj instanceof Instant instant) {
                        return instant.atOffset(ZoneOffset.UTC);
                    }
                    return (OffsetDateTime) obj;
                });

        // Step 3: Group source candles into weeks keyed by week-start (Monday at UTC midnight)
        TreeMap<OffsetDateTime, List<Candle>> weekGroups = sourceCandles.stream()
                .collect(Collectors.groupingBy(
                        candle -> candle.getOpenTime()
                                .withOffsetSameInstant(ZoneOffset.UTC)
                                .with(TemporalAdjusters.previousOrSame(weekStartDay))
                                .withHour(0)
                                .withMinute(0)
                                .withSecond(0)
                                .withNano(0),
                        TreeMap::new,
                        Collectors.toList()
                ));

        // Completeness boundary: UTC midnight today
        OffsetDateTime boundary = CandleFilters.utcMidnightToday(OffsetDateTime.now());

        ProcessingStats stats = new ProcessingStats();

        for (Map.Entry<OffsetDateTime, List<Candle>> entry : weekGroups.entrySet()) {
            List<Candle> weekCandles = entry.getValue();

            // Step 4: Completeness guard — must have exactly 7 source candles and last candle must be finalized
            if (weekCandles.size() != 7) {
                log.debug("Skipping incomplete week starting {} for {} — only {} candles", entry.getKey(), asset.getSymbol(), weekCandles.size());
                stats.skippedCount++;
                continue;
            }

            Candle lastCandle = weekCandles.get(weekCandles.size() - 1);
            if (!lastCandle.getCloseTime().isBefore(boundary)) {
                log.debug("Skipping current (unfinalized) week starting {} for {}", entry.getKey(), asset.getSymbol());
                stats.skippedCount++;
                continue;
            }

            // Step 5: Incremental guard — skip weeks already stored
            OffsetDateTime weekCloseTime = lastCandle.getCloseTime();
            if (lastStoredCloseTime.isPresent() && !weekCloseTime.isAfter(lastStoredCloseTime.get())) {
                log.debug("Skipping already-stored week closeTime={} for {}", weekCloseTime, asset.getSymbol());
                stats.skippedCount++;
                continue;
            }

            // Step 6: Aggregate OHLCV
            Candle firstCandle = weekCandles.get(0);
            BigDecimal high = weekCandles.stream()
                    .map(Candle::getHigh)
                    .max(BigDecimal::compareTo)
                    .orElse(firstCandle.getHigh());
            BigDecimal low = weekCandles.stream()
                    .map(Candle::getLow)
                    .min(BigDecimal::compareTo)
                    .orElse(firstCandle.getLow());
            BigDecimal volume = weekCandles.stream()
                    .map(Candle::getVolume)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Candle targetCandle = new Candle();
            targetCandle.setAsset(asset);
            targetCandle.setTimeframe(targetTimeframe);
            targetCandle.setOpenTime(firstCandle.getOpenTime());
            targetCandle.setCloseTime(weekCloseTime);
            targetCandle.setOpen(firstCandle.getOpen());
            targetCandle.setHigh(high);
            targetCandle.setLow(low);
            targetCandle.setClose(lastCandle.getClose());
            targetCandle.setVolume(volume);
            targetCandle.setSource(firstCandle.getSource());

            // Step 7: Idempotent upsert
            candleRepository.findByAssetAndTimeframeAndCloseTime(asset, targetTimeframe, weekCloseTime)
                    .ifPresentOrElse(
                            existing -> {
                                if (ohlcvChanged(existing, targetCandle)) {
                                    log.warn("Revision detected for {} {} at {}. Updating values.",
                                            asset.getSymbol(), targetTimeframe, weekCloseTime);
                                    updateOhlcv(existing, targetCandle);
                                    candleRepository.save(existing);
                                    stats.updatedCount++;
                                } else {
                                    stats.skippedCount++;
                                }
                            },
                            () -> {
                                candleRepository.save(targetCandle);
                                stats.insertedCount++;
                            }
                    );
        }

        log.info("Rollup summary for {} ({} -> {}): {} inserted, {} updated, {} skipped",
                asset.getSymbol(), sourceTimeframe, targetTimeframe,
                stats.insertedCount, stats.updatedCount, stats.skippedCount);
    }

    private boolean ohlcvChanged(Candle existing, Candle incoming) {
        return existing.getOpen().compareTo(incoming.getOpen()) != 0
                || existing.getHigh().compareTo(incoming.getHigh()) != 0
                || existing.getLow().compareTo(incoming.getLow()) != 0
                || existing.getClose().compareTo(incoming.getClose()) != 0
                || existing.getVolume().compareTo(incoming.getVolume()) != 0;
    }

    private void updateOhlcv(Candle existing, Candle incoming) {
        existing.setOpen(incoming.getOpen());
        existing.setHigh(incoming.getHigh());
        existing.setLow(incoming.getLow());
        existing.setClose(incoming.getClose());
        existing.setVolume(incoming.getVolume());
        existing.setOpenTime(incoming.getOpenTime());
        existing.setSource(incoming.getSource());
    }

    private static class ProcessingStats {
        int insertedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
    }
}
