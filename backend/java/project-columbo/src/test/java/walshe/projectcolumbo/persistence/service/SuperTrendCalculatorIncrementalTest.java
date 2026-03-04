package walshe.projectcolumbo.persistence.service;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.model.SuperTrendResult;
import walshe.projectcolumbo.persistence.service.SuperTrendCalculator;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SuperTrendCalculatorIncrementalTest {

    private final SuperTrendCalculator calculator = new SuperTrendCalculator();

    @Test
    void noPriorData_behavesLikeFullCalculation() {
        List<Candle> candles = makeSeries(8);
        int atrLength = 3;
        BigDecimal mult = new BigDecimal("2");

        List<SuperTrendResult> full = calculator.calculate(candles, atrLength, mult);
        List<SuperTrendResult> inc = calculator.calculateIncremental(candles, atrLength, mult, null, false);

        assertEquals(full.size(), inc.size());
        for (int i = 0; i < full.size(); i++) {
            assertEquals(full.get(i), inc.get(i));
        }
    }

    @Test
    void exactMatch_anchorReturnsOnlyNewAfterThat() {
        List<Candle> candles = makeSeries(10);
        int atrLength = 3;
        BigDecimal mult = new BigDecimal("1");

        List<SuperTrendResult> full = calculator.calculate(candles, atrLength, mult);
        OffsetDateTime lastStored = candles.get(4).getCloseTime();

        List<SuperTrendResult> inc = calculator.calculateIncremental(candles, atrLength, mult, lastStored, false);

        // Expected: all full results strictly after lastStored and non-null
        List<SuperTrendResult> expected = full.stream()
                .filter(r -> r != null && r.closeTime().isAfter(lastStored))
                .collect(Collectors.toList());

        assertEquals(expected.size(), inc.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).closeTime(), inc.get(i).closeTime());
            assertEquals(expected.get(i).supertrend(), inc.get(i).supertrend());
            assertEquals(expected.get(i).direction(), inc.get(i).direction());
        }
    }

    @Test
    void missingExactTime_usesFirstGreater() {
        List<Candle> candles = makeSeries(10);
        int atrLength = 3;
        BigDecimal mult = new BigDecimal("1");

        List<SuperTrendResult> full = calculator.calculate(candles, atrLength, mult);
        // Between index 3 and 4
        OffsetDateTime lastStored = candles.get(3).getCloseTime().plusSeconds(30);

        List<SuperTrendResult> inc = calculator.calculateIncremental(candles, atrLength, mult, lastStored, false);

        List<SuperTrendResult> expected = full.stream()
                .filter(r -> r != null && r.closeTime().isAfter(lastStored))
                .collect(Collectors.toList());

        assertEquals(expected.size(), inc.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).closeTime(), inc.get(i).closeTime());
            assertEquals(expected.get(i).supertrend(), inc.get(i).supertrend());
            assertEquals(expected.get(i).direction(), inc.get(i).direction());
        }
    }

    @Test
    void alreadyUpToDate_returnsEmpty() {
        List<Candle> candles = makeSeries(6);
        int atrLength = 3;
        BigDecimal mult = new BigDecimal("1");

        OffsetDateTime lastStored = candles.get(candles.size() - 1).getCloseTime();
        List<SuperTrendResult> inc = calculator.calculateIncremental(candles, atrLength, mult, lastStored, false);
        assertTrue(inc.isEmpty());
    }

    @Test
    void fullRecalc_flagIgnoresAnchor() {
        List<Candle> candles = makeSeries(7);
        int atrLength = 3;
        BigDecimal mult = new BigDecimal("1.5");

        List<SuperTrendResult> full = calculator.calculate(candles, atrLength, mult);
        OffsetDateTime lastStored = candles.get(4).getCloseTime();

        List<SuperTrendResult> inc = calculator.calculateIncremental(candles, atrLength, mult, lastStored, true);
        assertEquals(full.size(), inc.size());
        for (int i = 0; i < full.size(); i++) {
            assertEquals(full.get(i), inc.get(i));
        }
    }

    private List<Candle> makeSeries(int n) {
        List<Candle> out = new ArrayList<>();
        OffsetDateTime base = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        BigDecimal baseClose = new BigDecimal("100");
        for (int i = 0; i < n; i++) {
            BigDecimal close = baseClose.add(BigDecimal.valueOf(i));
            BigDecimal high = close.add(new BigDecimal("5"));
            BigDecimal low = close.subtract(new BigDecimal("5"));

            Candle c = new Candle();
            c.setCloseTime(base.plusMinutes(i));
            c.setClose(close);
            c.setHigh(high);
            c.setLow(low);
            c.setOpen(close); // open not used in calc
            out.add(c);
        }
        return out;
    }
}
