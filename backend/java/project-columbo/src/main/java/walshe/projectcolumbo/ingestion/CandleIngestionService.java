package walshe.projectcolumbo.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public CandleIngestionService(AssetRepository assetRepository,
                                  CandleRepository candleRepository,
                                  List<MarketDataProvider> marketDataProviders) {
        this.assetRepository = assetRepository;
        this.candleRepository = candleRepository;
        this.marketDataProviders = marketDataProviders;
    }

    public void ingestDaily() {
        List<Asset> activeAssets = assetRepository.findByActiveTrue();
        logger.info("Starting daily ingestion for {} active assets", activeAssets.size());

        for (Asset asset : activeAssets) {
            try {
                ingestForAsset(asset);
            } catch (Exception e) {
                logger.error("Failed to ingest data for asset: {}", asset.getSymbol(), e);
            }
        }
    }

    @Transactional
    public void ingestForAsset(Asset asset) {
        MarketDataProvider provider = findProvider(asset.getProvider());
        if (provider == null) {
            logger.warn("No provider found for {}", asset.getProvider());
            return;
        }

        List<CandleDto> dtos = provider.fetchDailyCandles(asset.getSymbol());

        Instant todayUtcStart = Instant.now().atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();

        List<Candle> finalizedCandles = dtos.stream()
                .filter(dto -> dto.closeTime().isBefore(todayUtcStart))
                .sorted(Comparator.comparing(CandleDto::closeTime))
                .map(dto -> mapToEntity(asset, dto))
                .toList();

        logger.info("Ingesting {} finalized candles for {}", finalizedCandles.size(), asset.getSymbol());

        // For Phase 3, we just save. Phase 4 will introduce ON CONFLICT.
        candleRepository.saveAll(finalizedCandles);
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
