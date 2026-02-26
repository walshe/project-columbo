package walshe.projectcolumbo.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import walshe.projectcolumbo.marketdata.CandleDto;
import walshe.projectcolumbo.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CandlePersistenceServiceTest {

    @Mock
    private CandleRepository candleRepository;

    private CandlePersistenceService candlePersistenceService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        candlePersistenceService = new CandlePersistenceService(candleRepository);
    }

    @Test
    void persistCandles_shouldFilterNonFinalizedCandles() {
        // Given
        Asset btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        Instant now = Instant.now();
        Instant todayUtcStart = now.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
        Instant yesterday = todayUtcStart.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = todayUtcStart.minus(2, ChronoUnit.DAYS);

        CandleDto oldCandle = new CandleDto(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("5"), BigDecimal.valueOf(100), twoDaysAgo.minus(1, ChronoUnit.DAYS), twoDaysAgo);
        CandleDto finalizedCandle = new CandleDto(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("5"), BigDecimal.valueOf(100), yesterday.minus(1, ChronoUnit.DAYS), yesterday);
        CandleDto nonFinalizedCandle = new CandleDto(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("5"), BigDecimal.valueOf(100), todayUtcStart, now);

        CandleIngestionService.IngestionStats stats = new CandleIngestionService.IngestionStats();

        // When
        candlePersistenceService.persistCandles(btc, List.of(oldCandle, finalizedCandle, nonFinalizedCandle), todayUtcStart, stats);

        // Then
        verify(candleRepository, times(2)).save(any(Candle.class));
        assertEquals(2, stats.insertedCount);
    }

    @Test
    void persistCandles_shouldDetectRevisions() {
        // Given
        Asset btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        Instant yesterday = Instant.now().atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant().minus(1, ChronoUnit.DAYS);
        OffsetDateTime yesterdayOd = OffsetDateTime.ofInstant(yesterday, ZoneOffset.UTC);

        CandleDto dto = new CandleDto(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("6"), BigDecimal.valueOf(100), yesterday.minus(1, ChronoUnit.DAYS), yesterday);

        Candle existing = new Candle();
        existing.setAsset(btc);
        existing.setTimeframe(Timeframe.D1);
        existing.setCloseTime(yesterdayOd);
        existing.setClose(new BigDecimal("5")); // Different from DTO
        existing.setOpen(BigDecimal.ONE);
        existing.setHigh(BigDecimal.TEN);
        existing.setLow(BigDecimal.ONE);
        existing.setVolume(BigDecimal.valueOf(100));
        existing.setSource(MarketProvider.BINANCE);

        when(candleRepository.findByAssetAndTimeframeAndCloseTime(eq(btc), eq(Timeframe.D1), eq(yesterdayOd)))
                .thenReturn(Optional.of(existing));

        CandleIngestionService.IngestionStats stats = new CandleIngestionService.IngestionStats();

        // When
        candlePersistenceService.persistCandles(btc, List.of(dto), yesterday.plus(1, ChronoUnit.DAYS), stats);

        // Then
        verify(candleRepository, times(1)).save(existing);
        assertEquals(0, existing.getClose().compareTo(new BigDecimal("6")));
        assertEquals(1, stats.updatedCount);
    }

    @Test
    void persistCandles_shouldSkipIfNoChange() {
        // Given
        Asset btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        Instant yesterday = Instant.now().atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant().minus(1, ChronoUnit.DAYS);
        OffsetDateTime yesterdayOd = OffsetDateTime.ofInstant(yesterday, ZoneOffset.UTC);

        CandleDto dto = new CandleDto(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("5"), BigDecimal.valueOf(100), yesterday.minus(1, ChronoUnit.DAYS), yesterday);

        Candle existing = new Candle();
        existing.setAsset(btc);
        existing.setTimeframe(Timeframe.D1);
        existing.setCloseTime(yesterdayOd);
        existing.setClose(new BigDecimal("5"));
        existing.setOpen(BigDecimal.ONE);
        existing.setHigh(BigDecimal.TEN);
        existing.setLow(BigDecimal.ONE);
        existing.setVolume(BigDecimal.valueOf(100));
        existing.setSource(MarketProvider.BINANCE);

        when(candleRepository.findByAssetAndTimeframeAndCloseTime(eq(btc), eq(Timeframe.D1), eq(yesterdayOd)))
                .thenReturn(Optional.of(existing));

        CandleIngestionService.IngestionStats stats = new CandleIngestionService.IngestionStats();

        // When
        candlePersistenceService.persistCandles(btc, List.of(dto), yesterday.plus(1, ChronoUnit.DAYS), stats);

        // Then
        verify(candleRepository, never()).save(any());
        assertEquals(1, stats.skippedCount);
    }
}
