package walshe.projectcolumbo.supertrend.freshness;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FreshnessServiceTest {

    private static final OffsetDateTime EXPECTED = OffsetDateTime.of(2024, 3, 14, 0, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void actualAtExpectedBoundaryIsUpToDate() {
        FreshnessStatus status = FreshnessService.evaluate(Timeframe.D1, EXPECTED, EXPECTED, EXPECTED.plusHours(1));

        assertThat(status.upToDate()).isTrue();
        assertThat(status.staleBeyondGraceWindow()).isFalse();
    }

    @Test
    void actualAheadOfExpectedBoundaryIsUpToDate() {
        FreshnessStatus status = FreshnessService.evaluate(Timeframe.D1, EXPECTED, EXPECTED.plusDays(1), EXPECTED.plusHours(1));

        assertThat(status.upToDate()).isTrue();
    }

    @Test
    void actualBehindExpectedBoundaryIsNotUpToDate() {
        FreshnessStatus status = FreshnessService.evaluate(Timeframe.D1, EXPECTED, EXPECTED.minusDays(1), EXPECTED.plusHours(1));

        assertThat(status.upToDate()).isFalse();
    }

    @Test
    void noCandleEverStoredIsNotUpToDate() {
        FreshnessStatus status = FreshnessService.evaluate(Timeframe.D1, EXPECTED, null, EXPECTED.plusHours(1));

        assertThat(status.upToDate()).isFalse();
        assertThat(status.actualLatestCloseTime()).isNull();
    }

    @Test
    void notUpToDateButWithinSixHoursOfBoundaryIsNotStale() {
        OffsetDateTime now = EXPECTED.plusHours(6); // exactly at the edge - not yet "more than" 6h

        FreshnessStatus status = FreshnessService.evaluate(Timeframe.D1, EXPECTED, null, now);

        assertThat(status.staleBeyondGraceWindow()).isFalse();
    }

    @Test
    void notUpToDateAndJustOverSixHoursPastBoundaryIsStale() {
        OffsetDateTime now = EXPECTED.plusHours(6).plusSeconds(1);

        FreshnessStatus status = FreshnessService.evaluate(Timeframe.D1, EXPECTED, null, now);

        assertThat(status.staleBeyondGraceWindow()).isTrue();
    }

    @Test
    void upToDateIsNeverStaleRegardlessOfElapsedTime() {
        OffsetDateTime now = EXPECTED.plusDays(30);

        FreshnessStatus status = FreshnessService.evaluate(Timeframe.D1, EXPECTED, EXPECTED, now);

        assertThat(status.staleBeyondGraceWindow()).isFalse();
    }
}
