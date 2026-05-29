package walshe.projectcolumbo.persistence.service;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.model.SuperTrendDirection;
import walshe.projectcolumbo.persistence.model.SuperTrendResult;
import walshe.projectcolumbo.persistence.service.SuperTrendCalculator;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SuperTrendCalculatorTest {

    private final SuperTrendCalculator calculator = new SuperTrendCalculator();

    @Test
    void shouldReturnEmptyListForEmptyInput() {
        List<SuperTrendResult> results = calculator.calculate(List.of(), 10, new BigDecimal("3"));
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldReturnNullsBeforeAtrLength() {
        List<Candle> candles = createCandles(5);
        List<SuperTrendResult> results = calculator.calculate(candles, 3, new BigDecimal("3"));

        assertEquals(5, results.size());
        assertNull(results.get(0));
        assertNull(results.get(1));
        assertNotNull(results.get(2));
        assertNotNull(results.get(3));
        assertNotNull(results.get(4));
    }

    @Test
    void shouldCalculateCorrectAtrInitial() {
        // TRs: 10, 10, 10
        // Initial ATR (n=3): (10+10+10)/3 = 10
        List<Candle> candles = List.of(
                createCandle(100, 110, 100, 105), // TR = 10
                createCandle(105, 115, 105, 110), // TR = 10
                createCandle(110, 120, 110, 115)  // TR = 10
        );

        List<SuperTrendResult> results = calculator.calculate(candles, 3, new BigDecimal("3"));
        assertEquals(new BigDecimal("10.0000000000"), results.get(2).atr());
    }

    @Test
    void shouldHandleDirectionSwitch() {
        // atrLength = 1, multiplier = 1
        // Candle 1: initial seed — close=100 >= lowerBand=80, so defaults UP (matches TradingView)
        // Candle 2: close=72 drops below prevFinalLower=80, flips to DOWN

        List<Candle> candles = List.of(
                createCandle(100, 110, 90, 100), // TR=20, ATR=20, Mid=100, BU=120, BL=80. ST=BL=80 (UP)
                createCandle(100, 85, 70, 72)    // TR=30, ATR=30, Mid=77.5, BU=107.5, BL=47.5.
                                                  // finalLower stays 80 (basicLower < prev, prevClose >= prev).
                                                  // Close=72 < finalLower=80 => DOWN. ST=FU=107.5
        );

        List<SuperTrendResult> results = calculator.calculate(candles, 1, new BigDecimal("1"));

        assertEquals(SuperTrendDirection.SUPERTREND_UP, results.get(0).direction());
        assertEquals(new BigDecimal("80.0000000000"), results.get(0).supertrend());

        assertEquals(SuperTrendDirection.SUPERTREND_DOWN, results.get(1).direction());
        assertEquals(new BigDecimal("107.5000000000"), results.get(1).supertrend());
    }

    private List<Candle> createCandles(int count) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            candles.add(createCandle(100, 110, 90, 100));
        }
        return candles;
    }

    private Candle createCandle(double open, double high, double low, double close) {
        Candle candle = new Candle();
        candle.setOpen(BigDecimal.valueOf(open));
        candle.setHigh(BigDecimal.valueOf(high));
        candle.setLow(BigDecimal.valueOf(low));
        candle.setClose(BigDecimal.valueOf(close));
        candle.setCloseTime(OffsetDateTime.now());
        return candle;
    }
}
