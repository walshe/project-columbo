package walshe.projectcolumbo.ingestion;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import walshe.projectcolumbo.marketdata.CandleDto;
import walshe.projectcolumbo.persistence.Asset;
import walshe.projectcolumbo.persistence.Candle;
import walshe.projectcolumbo.persistence.CandleRepository;
import walshe.projectcolumbo.persistence.Timeframe;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CandlePersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(CandlePersistenceService.class);

    private final CandleRepository candleRepository;

    @Transactional
    public CandleIngestionService.IngestionStats persistCandles(Asset asset, List<CandleDto> dtos,
                                                                Instant finalizedBoundary, CandleIngestionService.IngestionStats stats) {

        // Phase 5: Defensive Guard - Ensure close_time < finalizedBoundary
        List<Candle> finalizedCandles = dtos.stream()
                .filter(dto -> dto.closeTime().isBefore(finalizedBoundary))
                .sorted(Comparator.comparing(CandleDto::closeTime))
                .map(dto -> mapToEntity(asset, dto))
                .toList();

        logger.info("Processing {} finalized candles for {}", finalizedCandles.size(), asset.getSymbol());

        for (Candle incoming : finalizedCandles) {
            candleRepository.findByAssetAndTimeframeAndCloseTime(asset, Timeframe.D1, incoming.getCloseTime())
                    .ifPresentOrElse(
                            existing -> {
                                if (hasChanged(existing, incoming)) {
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
}
