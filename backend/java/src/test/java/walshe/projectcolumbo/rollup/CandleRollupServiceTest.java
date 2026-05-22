package walshe.projectcolumbo.rollup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.CandleRepository;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CandleRollupServiceTest {

    @Mock
    private CandleRepository candleRepository;

    @Mock
    private AssetRepository assetRepository;

    private CandleRollupService candleRollupService;

    private Asset testAsset;

    // Past finalized week: Mon 2025-01-06 through Sun 2025-01-12 UTC
    // Each D1 candle: openTime = day 00:00:00 UTC, closeTime = day 23:59:59.999 UTC
    private static final OffsetDateTime WEEK_MON = OffsetDateTime.of(2025, 1, 6, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime WEEK_TUE = OffsetDateTime.of(2025, 1, 7, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime WEEK_WED = OffsetDateTime.of(2025, 1, 8, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime WEEK_THU = OffsetDateTime.of(2025, 1, 9, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime WEEK_FRI = OffsetDateTime.of(2025, 1, 10, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime WEEK_SAT = OffsetDateTime.of(2025, 1, 11, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime WEEK_SUN = OffsetDateTime.of(2025, 1, 12, 0, 0, 0, 0, ZoneOffset.UTC);

    // closeTime = openTime + 24h - 1ms
    private static final long DAY_MINUS_1MS_NANOS = (24L * 3600L * 1_000_000_000L) - 1_000_000L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        candleRollupService = new CandleRollupService(assetRepository, candleRepository);
        testAsset = new Asset("BTCUSDT", "Bitcoin", MarketProvider.BINANCE, true);
        testAsset.setId(1L);
    }

    /**
     * Builds a D1 candle for the given open day with explicit OHLCV values.
     */
    private Candle d1Candle(OffsetDateTime openDay, BigDecimal open, BigDecimal high,
                             BigDecimal low, BigDecimal close, BigDecimal volume) {
        Candle c = new Candle();
        c.setAsset(testAsset);
        c.setTimeframe(Timeframe.D1);
        c.setOpenTime(openDay);
        c.setCloseTime(openDay.plusNanos(DAY_MINUS_1MS_NANOS));
        c.setOpen(open);
        c.setHigh(high);
        c.setLow(low);
        c.setClose(close);
        c.setVolume(volume);
        c.setSource(MarketProvider.BINANCE);
        return c;
    }

    /**
     * Returns a list of 7 D1 candles for the past week 2025-01-06 to 2025-01-12.
     * Fixed OHLCV values suitable for assertion:
     *   Mon open=100, high=110, low=90,  close=105, vol=1000
     *   Tue open=105, high=120, low=100, close=115, vol=1100
     *   Wed open=115, high=130, low=110, close=125, vol=1200
     *   Thu open=125, high=140, low=120, close=135, vol=1300
     *   Fri open=135, high=150, low=130, close=145, vol=1400
     *   Sat open=145, high=160, low=140, close=155, vol=1500
     *   Sun open=155, high=170, low=150, close=165, vol=1600
     *
     * Expected W1 rollup:
     *   open=100 (Mon), high=170, low=90, close=165 (Sun), volume=9100
     */
    private List<Candle> buildCompleteWeek() {
        List<Candle> candles = new ArrayList<>();
        candles.add(d1Candle(WEEK_MON, bd("100"), bd("110"), bd("90"),  bd("105"), bd("1000")));
        candles.add(d1Candle(WEEK_TUE, bd("105"), bd("120"), bd("100"), bd("115"), bd("1100")));
        candles.add(d1Candle(WEEK_WED, bd("115"), bd("130"), bd("110"), bd("125"), bd("1200")));
        candles.add(d1Candle(WEEK_THU, bd("125"), bd("140"), bd("120"), bd("135"), bd("1300")));
        candles.add(d1Candle(WEEK_FRI, bd("135"), bd("150"), bd("130"), bd("145"), bd("1400")));
        candles.add(d1Candle(WEEK_SAT, bd("145"), bd("160"), bd("140"), bd("155"), bd("1500")));
        candles.add(d1Candle(WEEK_SUN, bd("155"), bd("170"), bd("150"), bd("165"), bd("1600")));
        return candles;
    }

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }

    // -------------------------------------------------------------------------
    // Test 1: OHLCV correctness
    // -------------------------------------------------------------------------

    @Test
    void rollup_producesCorrectOHLCV() {
        List<Candle> week = buildCompleteWeek();
        when(assetRepository.findByActiveTrue()).thenReturn(List.of(testAsset));
        when(candleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(testAsset, Timeframe.D1))
                .thenReturn(week);
        when(candleRepository.findLatestCloseTime(testAsset.getId(), Timeframe.W1.name()))
                .thenReturn(Optional.empty());
        when(candleRepository.findByAssetAndTimeframeAndCloseTime(eq(testAsset), eq(Timeframe.W1), any()))
                .thenReturn(Optional.empty());

        ArgumentCaptor<Candle> captor = ArgumentCaptor.forClass(Candle.class);

        candleRollupService.rollupForAllActiveAssets(Timeframe.D1, Timeframe.W1, DayOfWeek.MONDAY);

        verify(candleRepository).save(captor.capture());
        Candle result = captor.getValue();

        assertEquals(0, bd("100").compareTo(result.getOpen()),  "open should be Monday open");
        assertEquals(0, bd("170").compareTo(result.getHigh()),  "high should be max of daily highs");
        assertEquals(0, bd("90").compareTo(result.getLow()),    "low should be min of daily lows");
        assertEquals(0, bd("165").compareTo(result.getClose()), "close should be Sunday close");
        assertEquals(0, bd("9100").compareTo(result.getVolume()), "volume should be sum of daily volumes");
    }

    // -------------------------------------------------------------------------
    // Test 2: openTime and closeTime taken from data, not calendar arithmetic
    // -------------------------------------------------------------------------

    @Test
    void rollup_usesCorrectOpenClose() {
        List<Candle> week = buildCompleteWeek();
        OffsetDateTime expectedOpenTime  = WEEK_MON;                              // Mon 00:00:00
        OffsetDateTime expectedCloseTime = WEEK_SUN.plusNanos(DAY_MINUS_1MS_NANOS); // Sun 23:59:59.999

        when(assetRepository.findByActiveTrue()).thenReturn(List.of(testAsset));
        when(candleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(testAsset, Timeframe.D1))
                .thenReturn(week);
        when(candleRepository.findLatestCloseTime(testAsset.getId(), Timeframe.W1.name()))
                .thenReturn(Optional.empty());
        when(candleRepository.findByAssetAndTimeframeAndCloseTime(eq(testAsset), eq(Timeframe.W1), any()))
                .thenReturn(Optional.empty());

        ArgumentCaptor<Candle> captor = ArgumentCaptor.forClass(Candle.class);

        candleRollupService.rollupForAllActiveAssets(Timeframe.D1, Timeframe.W1, DayOfWeek.MONDAY);

        verify(candleRepository).save(captor.capture());
        Candle result = captor.getValue();

        assertEquals(expectedOpenTime,  result.getOpenTime(),  "openTime must be Monday candle openTime from data");
        assertEquals(expectedCloseTime, result.getCloseTime(), "closeTime must be Sunday candle closeTime from data");
    }

    // -------------------------------------------------------------------------
    // Test 3: Incomplete week produces no candle
    // -------------------------------------------------------------------------

    @Test
    void rollup_skipIncompleteWeek() {
        // Only 5 candles in the week — incomplete, no rollup expected
        List<Candle> incomplete = new ArrayList<>(buildCompleteWeek().subList(0, 5));
        when(assetRepository.findByActiveTrue()).thenReturn(List.of(testAsset));
        when(candleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(testAsset, Timeframe.D1))
                .thenReturn(incomplete);
        when(candleRepository.findLatestCloseTime(testAsset.getId(), Timeframe.W1.name()))
                .thenReturn(Optional.empty());

        candleRollupService.rollupForAllActiveAssets(Timeframe.D1, Timeframe.W1, DayOfWeek.MONDAY);

        verify(candleRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Test 4: Timeframe-generic — targetTimeframe drives the produced candle's timeframe
    // -------------------------------------------------------------------------

    @Test
    void rollup_isParameterized() {
        // Use a dummy "target" timeframe — here we pass W1 explicitly as target.
        // The produced candle's getTimeframe() must equal targetTimeframe (W1),
        // proving no D1/W1 literal is hardcoded inside the service.
        List<Candle> week = buildCompleteWeek();
        Timeframe sourceTimeframe = Timeframe.D1;
        Timeframe targetTimeframe = Timeframe.W1;

        when(assetRepository.findByActiveTrue()).thenReturn(List.of(testAsset));
        when(candleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(testAsset, sourceTimeframe))
                .thenReturn(week);
        when(candleRepository.findLatestCloseTime(testAsset.getId(), targetTimeframe.name()))
                .thenReturn(Optional.empty());
        when(candleRepository.findByAssetAndTimeframeAndCloseTime(eq(testAsset), eq(targetTimeframe), any()))
                .thenReturn(Optional.empty());

        ArgumentCaptor<Candle> captor = ArgumentCaptor.forClass(Candle.class);

        candleRollupService.rollupForAllActiveAssets(sourceTimeframe, targetTimeframe, DayOfWeek.MONDAY);

        verify(candleRepository).save(captor.capture());
        assertEquals(targetTimeframe, captor.getValue().getTimeframe(),
                "produced candle's timeframe must equal the targetTimeframe parameter");
    }

    // -------------------------------------------------------------------------
    // Test 5: Already-stored weeks are skipped (incremental detection)
    // -------------------------------------------------------------------------

    @Test
    void rollup_skipsAlreadyStoredWeeks() {
        // The week's Sunday closeTime is 2025-01-12 23:59:59.999 UTC.
        // If the last stored target closeTime is already at or after that, skip.
        List<Candle> week = buildCompleteWeek();
        OffsetDateTime sundayCloseTime = WEEK_SUN.plusNanos(DAY_MINUS_1MS_NANOS);

        when(assetRepository.findByActiveTrue()).thenReturn(List.of(testAsset));
        when(candleRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(testAsset, Timeframe.D1))
                .thenReturn(week);
        // Return the Sunday closeTime as already stored — week is not new
        when(candleRepository.findLatestCloseTime(testAsset.getId(), Timeframe.W1.name()))
                .thenReturn(Optional.of(sundayCloseTime));

        candleRollupService.rollupForAllActiveAssets(Timeframe.D1, Timeframe.W1, DayOfWeek.MONDAY);

        verify(candleRepository, never()).save(any());
    }
}
