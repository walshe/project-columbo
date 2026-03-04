package walshe.projectcolumbo.persistence.service;
import walshe.projectcolumbo.persistence.entity.Candle;
import walshe.projectcolumbo.persistence.service.CandleFilters;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CandleFiltersTest {

    @Test
    void utcMidnightTodayComputesStartOfUtcDay() {
        OffsetDateTime now = OffsetDateTime.of(2026, 2, 20, 21, 51, 0, 0, ZoneOffset.UTC);
        OffsetDateTime midnight = CandleFilters.utcMidnightToday(now);
        assertEquals(OffsetDateTime.of(2026, 2, 20, 0, 0, 0, 0, ZoneOffset.UTC), midnight);
    }

    @Test
    void finalizedBeforeUtcMidnightTodayFiltersAndSorts() {
        OffsetDateTime now = OffsetDateTime.of(2026, 2, 20, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime midnight = CandleFilters.utcMidnightToday(now);

        Candle a = candleAt(midnight.minusDays(1).plusHours(10), 100);
        Candle b = candleAt(midnight.minusHours(1), 101);
        Candle c = candleAt(midnight.plusHours(5), 102); // not finalized
        Candle d = candleAt(midnight.minusDays(2), 99);

        List<Candle> filtered = CandleFilters.finalizedBeforeUtcMidnightToday(List.of(b, d, c, a), now);
        assertEquals(List.of(d, a, b), filtered);
        assertTrue(filtered.stream().allMatch(x -> x.getCloseTime().isBefore(midnight)));
    }

    private Candle candleAt(OffsetDateTime closeTime, double close) {
        Candle c = new Candle();
        c.setCloseTime(closeTime);
        c.setClose(BigDecimal.valueOf(close));
        c.setHigh(BigDecimal.valueOf(close));
        c.setLow(BigDecimal.valueOf(close));
        c.setOpen(BigDecimal.valueOf(close));
        return c;
    }
}
