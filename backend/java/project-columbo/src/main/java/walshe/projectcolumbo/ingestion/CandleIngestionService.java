package walshe.projectcolumbo.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import walshe.projectcolumbo.marketdata.CandleDto;
import walshe.projectcolumbo.marketdata.MarketDataProvider;
import walshe.projectcolumbo.persistence.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
public class CandleIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(CandleIngestionService.class);

    private final AssetRepository assetRepository;
    private final CandleRepository candleRepository;
    private final List<MarketDataProvider> marketDataProviders;
    private final IngestionOrchestrator orchestrator;

    public CandleIngestionService(AssetRepository assetRepository,
                                  CandleRepository candleRepository,
                                  List<MarketDataProvider> marketDataProviders,
                                  @org.springframework.context.annotation.Lazy IngestionOrchestrator orchestrator) {
        this.assetRepository = assetRepository;
        this.candleRepository = candleRepository;
        this.marketDataProviders = marketDataProviders;
        this.orchestrator = orchestrator;
    }

    @Scheduled(cron = "${app.ingestion.cron}")
    public void scheduledIngest() {
        logger.info("Triggering scheduled daily ingestion");
        try {
            orchestrator.runInternal(MarketProvider.BINANCE, Timeframe.D1);
            logger.info("Scheduled daily ingestion completed successfully");
        } catch (IngestionAlreadyRunningException e) {
            logger.info("Scheduled daily ingestion skipped - already running: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Scheduled daily ingestion failed", e);
        }
    }

    public IngestionStats ingestDaily() {
        List<Asset> activeAssets = assetRepository.findByActiveTrue();
        logger.info("Starting daily ingestion for {} active assets", activeAssets.size());

        IngestionStats totalStats = new IngestionStats();

        for (Asset asset : activeAssets) {
            try {
                IngestionStats assetStats = ingestForAsset(asset);
                totalStats.add(assetStats);
            } catch (Exception e) {
                logger.error("Failed to ingest data for asset: {}", asset.getSymbol(), e);
                totalStats.errorCount++;
                if (totalStats.firstErrorMessage == null) {
                    totalStats.firstErrorMessage = e.getMessage();
                }
            }
        }

        logger.info("Daily ingestion summary: {} inserted, {} updated, {} skipped, {} errors across {} assets",
                totalStats.insertedCount, totalStats.updatedCount, totalStats.skippedCount,
                totalStats.errorCount, activeAssets.size());

        return totalStats;
    }

    @Transactional
    public IngestionStats ingestForAsset(Asset asset) {
        IngestionStats stats = new IngestionStats();
        MarketDataProvider provider = findProvider(asset.getProvider());
        if (provider == null) {
            logger.warn("No provider found for {}", asset.getProvider());
            stats.errorCount++;
            stats.firstErrorMessage = "No provider found for " + asset.getProvider();
            return stats;
        }

        List<CandleDto> dtos = provider.fetchDailyCandles(asset.getSymbol(), null, null);

        Instant todayUtcStart = Instant.now().atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();

        List<Candle> finalizedCandles = dtos.stream()
                .filter(dto -> dto.closeTime().isBefore(todayUtcStart))
                .sorted(Comparator.comparing(CandleDto::closeTime))
                .map(dto -> mapToEntity(asset, dto))
                .toList();

        logger.info("Processing {} finalized candles for {}", finalizedCandles.size(), asset.getSymbol());

        for (Candle incoming : finalizedCandles) {
            candleRepository.findByAssetAndTimeframeAndCloseTime(asset, Timeframe.D1, incoming.getCloseTime())
                    .ifPresentOrElse(
                            existing -> {
                                if (hasChanged(existing, incoming)) {
                                    logger.warn("Revision detected for {} at {}. Updating fields.",
                                            asset.getSymbol(), incoming.getCloseTime());
                                    updateFields(existing, incoming);
                                    candleRepository.save(existing);
                                    stats.updatedCount++;
                                } else {
                                    stats.skippedCount++;
                                }
                            },
                            () -> {
                                candleRepository.save(incoming);
                                stats.insertedCount++;
                            }
                    );
        }

        return stats;
    }

    private boolean hasChanged(Candle existing, Candle incoming) {
        return existing.getOpen().compareTo(incoming.getOpen()) != 0 ||
                existing.getHigh().compareTo(incoming.getHigh()) != 0 ||
                existing.getLow().compareTo(incoming.getLow()) != 0 ||
                existing.getClose().compareTo(incoming.getClose()) != 0 ||
                existing.getVolume().compareTo(incoming.getVolume()) != 0 ||
                existing.getSource() != incoming.getSource();
    }

    private void updateFields(Candle existing, Candle incoming) {
        existing.setOpen(incoming.getOpen());
        existing.setHigh(incoming.getHigh());
        existing.setLow(incoming.getLow());
        existing.setClose(incoming.getClose());
        existing.setVolume(incoming.getVolume());
        existing.setSource(incoming.getSource());
        existing.setOpenTime(incoming.getOpenTime());
        if (incoming.getRawPayload() != null) {
            existing.setRawPayload(incoming.getRawPayload());
        }
    }

    public static class IngestionStats {
        public int insertedCount = 0;
        public int updatedCount = 0;
        public int skippedCount = 0;
        public int errorCount = 0;
        public String firstErrorMessage = null;

        public void add(IngestionStats other) {
            this.insertedCount += other.insertedCount;
            this.updatedCount += other.updatedCount;
            this.skippedCount += other.skippedCount;
            this.errorCount += other.errorCount;
            if (this.firstErrorMessage == null && other.firstErrorMessage != null) {
                this.firstErrorMessage = other.firstErrorMessage;
            }
        }
    }

    private MarketDataProvider findProvider(MarketProvider providerType) {
        return marketDataProviders.stream()
                .filter(p -> p.getProviderName().equalsIgnoreCase(providerType.name()))
                .findFirst()
                .orElse(null);
    }

    private Candle mapToEntity(Asset asset, CandleDto dto) {
        Candle candle = new Candle();
        candle.setAsset(asset);
        candle.setTimeframe(Timeframe.D1);
        candle.setOpenTime(OffsetDateTime.ofInstant(dto.openTime(), ZoneOffset.UTC));
        candle.setCloseTime(OffsetDateTime.ofInstant(dto.closeTime(), ZoneOffset.UTC));
        candle.setOpen(dto.open());
        candle.setHigh(dto.high());
        candle.setLow(dto.low());
        candle.setClose(dto.close());
        candle.setVolume(dto.volume());
        candle.setSource(asset.getProvider());
        // rawPayload could be added if available in DTO
        return candle;
    }
}
