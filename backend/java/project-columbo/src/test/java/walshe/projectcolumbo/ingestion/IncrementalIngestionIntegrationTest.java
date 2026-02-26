package walshe.projectcolumbo.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import walshe.projectcolumbo.TestcontainersConfiguration;
import walshe.projectcolumbo.marketdata.CandleDto;
import walshe.projectcolumbo.marketdata.MarketDataProvider;
import walshe.projectcolumbo.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class IncrementalIngestionIntegrationTest {

    @Autowired
    private CandleIngestionService candleIngestionService;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private CandleRepository candleRepository;

    @MockitoBean(name = "binanceMarketDataProvider")
    private MarketDataProvider binanceProvider;

    private Asset btc;

    @BeforeEach
    void setUp() {
        candleRepository.deleteAll();
        assetRepository.deleteAll();

        when(binanceProvider.getProviderName()).thenReturn("BINANCE");

        btc = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        btc = assetRepository.save(btc);
    }

    @Test
    void scenario1_initialBackfill() {
        // Given: Empty database
        Instant pastTodayStart = Instant.parse("2020-01-10T00:00:00Z");
        Instant yesterday = pastTodayStart.minus(1, ChronoUnit.DAYS);

        CandleDto dto = new CandleDto(
                new BigDecimal("50000"), new BigDecimal("51000"), new BigDecimal("49000"), new BigDecimal("50500"),
                new BigDecimal("100"), yesterday.minus(1, ChronoUnit.DAYS), yesterday.minusMillis(1)
        );

        when(binanceProvider.fetchDailyCandles(eq("BTCUSDT"), any(), any())).thenReturn(List.of(dto));

        // When
        candleIngestionService.ingestDaily();

        // Then
        List<Candle> candles = candleRepository.findByAssetAndTimeframe(btc, Timeframe.D1);
        assertThat(candles).hasSize(1);
        assertThat(candles.get(0).getClose()).isEqualByComparingTo("50500");
        
        // Let's verify provider was called with SOME startTime (not null)
        verify(binanceProvider).fetchDailyCandles(eq("BTCUSDT"), anyLong(), anyLong());
    }

    @Test
    void scenario2_incrementalDelta() {
        // Given: DB contains candles up to yesterday
        // We use a date far in the past to ensure getFinalizedBoundary() is always > this date
        Instant pastTodayStart = Instant.parse("2020-01-10T00:00:00Z");
        Instant yesterdayStart = pastTodayStart.minus(1, ChronoUnit.DAYS);
        Instant dayBeforeYesterdayStart = yesterdayStart.minus(1, ChronoUnit.DAYS);

        Candle oldCandle = new Candle();
        oldCandle.setAsset(btc);
        oldCandle.setTimeframe(Timeframe.D1);
        oldCandle.setOpenTime(dayBeforeYesterdayStart.atOffset(ZoneOffset.UTC));
        oldCandle.setCloseTime(yesterdayStart.atOffset(ZoneOffset.UTC));
        oldCandle.setOpen(BigDecimal.ONE);
        oldCandle.setHigh(BigDecimal.TEN);
        oldCandle.setLow(BigDecimal.ONE);
        oldCandle.setClose(BigDecimal.ONE);
        oldCandle.setVolume(BigDecimal.ONE);
        oldCandle.setSource(MarketProvider.BINANCE);
        candleRepository.save(oldCandle);

        // New candle from provider (yesterday's candle)
        CandleDto newDto = new CandleDto(
                new BigDecimal("60000"), new BigDecimal("61000"), new BigDecimal("59000"), new BigDecimal("60500"),
                new BigDecimal("200"), yesterdayStart, pastTodayStart.minusMillis(1)
        );

        when(binanceProvider.fetchDailyCandles(eq("BTCUSDT"), anyLong(), anyLong())).thenReturn(List.of(newDto));

        // When
        candleIngestionService.ingestDaily();

        // Then
        List<Candle> candles = candleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(btc, Timeframe.D1);
        assertThat(candles).hasSize(2);
        assertThat(candles.get(1).getClose()).isEqualByComparingTo("60500");

        // Verify startTime was lastClose + 1ms
        long expectedStartTime = yesterdayStart.toEpochMilli() + 1;
        verify(binanceProvider).fetchDailyCandles(eq("BTCUSDT"), eq(expectedStartTime), anyLong());
    }

    @Test
    void scenario3_noDelta() {
        // Given: DB fully up to date (contains yesterday's candle)
        // finalizedBoundary is UTC midnight today.
        // If today is 2024-01-10, finalizedBoundary is 2024-01-10T00:00:00Z.
        // Yesterday's candle should end at 2024-01-10T00:00:00Z.
        
        // However, the test service uses REAL clock for finalizedBoundary.
        // We must use a date that is definitely in the future relative to 2024-01-10
        // to ensure the real clock-based finalizedBoundary is <= our candle's close time.
        
        Instant realTodayStart = Instant.now().atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
        Instant yesterdayStart = realTodayStart.minus(1, ChronoUnit.DAYS);

        Candle yesterdayCandle = new Candle();
        yesterdayCandle.setAsset(btc);
        yesterdayCandle.setTimeframe(Timeframe.D1);
        yesterdayCandle.setOpenTime(yesterdayStart.atOffset(ZoneOffset.UTC));
        yesterdayCandle.setCloseTime(realTodayStart.atOffset(ZoneOffset.UTC));
        yesterdayCandle.setOpen(BigDecimal.ONE);
        yesterdayCandle.setHigh(BigDecimal.TEN);
        yesterdayCandle.setLow(BigDecimal.ONE);
        yesterdayCandle.setClose(BigDecimal.ONE);
        yesterdayCandle.setVolume(BigDecimal.ONE);
        yesterdayCandle.setSource(MarketProvider.BINANCE);
        candleRepository.save(yesterdayCandle);

        // When
        candleIngestionService.ingestDaily();

        // Then
        verify(binanceProvider, never()).fetchDailyCandles(any(), any(), any());
    }

    @Test
    void scenario4_idempotency() {
        // Given: Data already fetched once
        Instant pastTodayStart = Instant.parse("2020-01-10T00:00:00Z");
        Instant yesterdayStart = pastTodayStart.minus(1, ChronoUnit.DAYS);

        CandleDto dto = new CandleDto(
                new BigDecimal("50000"), new BigDecimal("51000"), new BigDecimal("49000"), new BigDecimal("50500"),
                new BigDecimal("100"), yesterdayStart.minus(1, ChronoUnit.DAYS), yesterdayStart.minusMillis(1)
        );

        when(binanceProvider.fetchDailyCandles(eq("BTCUSDT"), any(), any())).thenReturn(List.of(dto));

        // When: Run twice
        candleIngestionService.ingestDaily();
        int countAfterFirst = candleRepository.findByAssetAndTimeframe(btc, Timeframe.D1).size();
        
        // Reset mock for second call because it would use a different window if first one saved data
        // Actually, let's just use the same mock response but it should skip or upsert without change
        candleIngestionService.ingestDaily();
        int countAfterSecond = candleRepository.findByAssetAndTimeframe(btc, Timeframe.D1).size();

        // Then
        assertThat(countAfterFirst).isEqualTo(1);
        assertThat(countAfterSecond).isEqualTo(1);
    }
}
