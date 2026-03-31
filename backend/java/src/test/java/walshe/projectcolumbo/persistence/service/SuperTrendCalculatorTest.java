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
        // Middle = (H+L)/2
        // basicUpper = Middle + ATR
        // basicLower = Middle - ATR
        
        List<Candle> candles = List.of(
                createCandle(100, 110, 90, 100), // TR=20, ATR=20, Mid=100, BU=120, BL=80, Close=100. ST=120 (Down)
                createCandle(100, 130, 120, 125) // TR=30 (130-100), ATR=(20*0+30)/1=30, Mid=125, BU=155, BL=95. Close=125.
                                                 // prevST=120, Close=125 > 120 => Switch to UP. ST=BL=95.
        );

        List<SuperTrendResult> results = calculator.calculate(candles, 1, new BigDecimal("1"));

        assertEquals(SuperTrendDirection.DOWN, results.get(0).direction());
        assertEquals(new BigDecimal("120.0000000000"), results.get(0).supertrend());

        assertEquals(SuperTrendDirection.UP, results.get(1).direction());
        assertEquals(new BigDecimal("95.0000000000"), results.get(1).supertrend());
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
