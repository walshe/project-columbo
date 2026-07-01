package walshe.projectcolumbo.api.v1;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import walshe.projectcolumbo.config.TimeProvider;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.CandleRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandleFreshnessServiceTest {

    @Mock
    private CandleRepository candleRepository;

    // Wed 2026-07-01 → boundary 2026-07-01T00:00Z; D1 expectedLatest = 2026-06-30T00:00Z
    private static final OffsetDateTime NOON = OffsetDateTime.of(2026, 7, 1, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime EARLY = OffsetDateTime.of(2026, 7, 1, 3, 0, 0, 0, ZoneOffset.UTC);

    private CandleFreshnessService serviceAt(OffsetDateTime now) {
        TimeProvider clock = () -> now;
        return new CandleFreshnessService(candleRepository, clock);
    }

    private void stubLatestD1(OffsetDateTime latest) {
        when(candleRepository.findLatestCloseTimeForTimeframe(eq(Timeframe.D1.name())))
                .thenReturn(latest == null ? Optional.empty() : Optional.of(latest));
    }

    @Test
    void isUpToDateWhenLatestReachesExpected() {
        stubLatestD1(OffsetDateTime.of(2026, 6, 30, 23, 59, 59, 0, ZoneOffset.UTC));
        assertThat(serviceAt(NOON).isUpToDate(Timeframe.D1)).isTrue();
    }

    @Test
    void notUpToDateWhenLatestIsBehind() {
        stubLatestD1(OffsetDateTime.of(2026, 6, 29, 23, 59, 59, 0, ZoneOffset.UTC));
        assertThat(serviceAt(NOON).isUpToDate(Timeframe.D1)).isFalse();
    }

    @Test
    void notUpToDateWhenNoCandles() {
        stubLatestD1(null);
        assertThat(serviceAt(NOON).isUpToDate(Timeframe.D1)).isFalse();
    }

    @Test
    void staleBeyondGraceWhenBehindAndPastGraceWindow() {
        stubLatestD1(OffsetDateTime.of(2026, 6, 29, 23, 59, 59, 0, ZoneOffset.UTC));
        // NOON (12:00) is past boundary + 6h grace
        assertThat(serviceAt(NOON).isStaleBeyondGrace(Timeframe.D1)).isTrue();
    }

    @Test
    void notStaleBeyondGraceWithinGraceWindow() {
        stubLatestD1(OffsetDateTime.of(2026, 6, 29, 23, 59, 59, 0, ZoneOffset.UTC));
        // EARLY (03:00) is within the 6h grace after the boundary — lenient despite being behind
        assertThat(serviceAt(EARLY).isStaleBeyondGrace(Timeframe.D1)).isFalse();
    }

    @Test
    void notStaleBeyondGraceWhenUpToDate() {
        stubLatestD1(OffsetDateTime.of(2026, 6, 30, 23, 59, 59, 0, ZoneOffset.UTC));
        assertThat(serviceAt(NOON).isStaleBeyondGrace(Timeframe.D1)).isFalse();
    }

    @Test
    void expectedLatestIsPeriodStart() {
        assertThat(serviceAt(NOON).expectedLatest(Timeframe.D1))
                .isEqualTo(OffsetDateTime.of(2026, 6, 30, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(serviceAt(NOON).expectedLatest(Timeframe.W1))
                .isEqualTo(OffsetDateTime.of(2026, 6, 24, 0, 0, 0, 0, ZoneOffset.UTC));
    }
}
