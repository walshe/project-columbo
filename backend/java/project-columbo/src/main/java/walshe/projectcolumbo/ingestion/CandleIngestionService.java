package walshe.projectcolumbo.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
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
    private final IngestionProperties ingestionProperties;
    private final CandlePersistenceService candlePersistenceService;

    public CandleIngestionService(AssetRepository assetRepository,
                                  CandleRepository candleRepository,
                                  List<MarketDataProvider> marketDataProviders,
                                  @org.springframework.context.annotation.Lazy IngestionOrchestrator orchestrator,
                                  IngestionProperties ingestionProperties,
                                  CandlePersistenceService candlePersistenceService) {
        this.assetRepository = assetRepository;
        this.candleRepository = candleRepository;
        this.marketDataProviders = marketDataProviders;
        this.orchestrator = orchestrator;
        this.ingestionProperties = ingestionProperties;
        this.candlePersistenceService = candlePersistenceService;
    }

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

        boolean interrupted = false;

        for (Asset asset : activeAssets) {

            if (interrupted) break;

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

            // Polite delay between assets — avoids hammering Binance
            // TODO use Guava's RateLimiter in BinanceMarketDataProvider
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.warn("Ingestion interrupted during sleep");
                interrupted = true;
            }
        }

        logger.info("Daily ingestion summary: {} inserted, {} updated, {} skipped, {} errors across {} assets",
                totalStats.insertedCount, totalStats.updatedCount, totalStats.skippedCount,
                totalStats.errorCount, activeAssets.size());

        return totalStats;
    }

    public IngestionStats ingestForAsset(Asset asset) {
        IngestionStats stats = new IngestionStats();
        MarketDataProvider provider = findProvider(asset.getProvider());
        if (provider == null) {
            logger.warn("No provider found for {}", asset.getProvider());
            stats.errorCount++;
            stats.firstErrorMessage = "No provider found for " + asset.getProvider();
            return stats;
        }

        // Phase 4.1: Compute finalized boundary (UTC start of current day)
        Instant finalizedBoundary = getFinalizedBoundary();

        // Phase 4.2: Compute time window
        OffsetDateTime lastClose = candleRepository
                .findLatestCloseTime(asset.getId(), Timeframe.D1.name())
                .map(obj -> {
                    if (obj instanceof Instant instant) {
                        return instant.atOffset(ZoneOffset.UTC);
                    }
                    return (OffsetDateTime) obj;
                })
                .orElse(null);

        Long startTimeMs = (lastClose != null)
                ? lastClose.toInstant().toEpochMilli() + 1
                : ingestionProperties.backfillStartEpochMs();

        Long endTimeMs = finalizedBoundary.toEpochMilli();

        logger.info("INGESTION_WINDOW asset={} start={} end={}", asset.getSymbol(), startTimeMs, endTimeMs);

        // Phase 4.3: Skip condition
        if (startTimeMs == null || startTimeMs >= endTimeMs) {
            logger.info("No new candles required for {}. Skipping.", asset.getSymbol());
            return stats;
        }

        List<CandleDto> dtos;
        try {
            dtos = provider.fetchDailyCandles(asset.getSymbol(), startTimeMs, endTimeMs);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().is4xxClientError() && e.getResponseBodyAsString().contains("-1121")) {
                logger.error("Binance returned Invalid Symbol (-1121) for {}. Marking as inactive.", asset.getSymbol());
                asset.setActive(false);
                assetRepository.save(asset);
                stats.errorCount++;
                stats.firstErrorMessage = "Invalid symbol: " + asset.getSymbol();
                return stats;
            }
            throw e;
        }

        return candlePersistenceService.persistCandles(asset, dtos, finalizedBoundary, stats);

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

    private Instant getFinalizedBoundary() {
        return Instant.now().atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
    }

    private MarketDataProvider findProvider(MarketProvider providerType) {
        return marketDataProviders.stream()
                .filter(p -> p.getProviderName().equalsIgnoreCase(providerType.name()))
                .findFirst()
                .orElse(null);
    }
}
