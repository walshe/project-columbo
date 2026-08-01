package walshe.projectcolumbo.supertrend.freshness;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FreshnessBoundaryTest {

    // Mid-afternoon UTC on purpose: the boundary must key off UTC midnight regardless of time-of-day.
    private static final OffsetDateTime NOW = OffsetDateTime.of(2024, 3, 15, 14, 30, 0, 0, ZoneOffset.UTC);

    @Test
    void d1BoundaryIsYesterdaysUtcMidnight() {
        OffsetDateTime expected = FreshnessBoundary.expectedLatestCloseTime(Timeframe.D1, NOW);

        assertThat(expected).isEqualTo(OffsetDateTime.of(2024, 3, 14, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void w1BoundaryIsSevenDaysBeforeUtcMidnightToday() {
        OffsetDateTime expected = FreshnessBoundary.expectedLatestCloseTime(Timeframe.W1, NOW);

        assertThat(expected).isEqualTo(OffsetDateTime.of(2024, 3, 8, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void boundaryIsUnaffectedByNonUtcOffsetOnTheInput() {
        OffsetDateTime nowInAnotherZone = NOW.withOffsetSameInstant(ZoneOffset.ofHours(9));

        OffsetDateTime expected = FreshnessBoundary.expectedLatestCloseTime(Timeframe.D1, nowInAnotherZone);

        assertThat(expected).isEqualTo(OffsetDateTime.of(2024, 3, 14, 0, 0, 0, 0, ZoneOffset.UTC));
    }
}
