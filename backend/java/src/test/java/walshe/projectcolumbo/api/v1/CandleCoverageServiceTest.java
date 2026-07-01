package walshe.projectcolumbo.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import walshe.projectcolumbo.api.v1.dto.CandleCoverageDto;
import walshe.projectcolumbo.config.TimeProvider;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.CandleRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandleCoverageServiceTest {

    @Mock
    private CandleRepository candleRepository;

    @Mock
    private TimeProvider timeProvider;

    private CandleCoverageService service;

    // Wed 2026-07-01 12:00 UTC → finalized boundary is 2026-07-01T00:00Z
    private final OffsetDateTime now = OffsetDateTime.of(2026, 7, 1, 12, 0, 0, 0, ZoneOffset.UTC);
    private final OffsetDateTime boundary = OffsetDateTime.of(2026, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new CandleCoverageService(candleRepository, timeProvider);
        when(timeProvider.now()).thenReturn(now);
    }

    @Test
    void reportsUpToDateWhenLatestReachesExpected() {
        // D1 expectedLatest = boundary - 1 day = 2026-06-30T00:00Z; latest = yesterday's close
        OffsetDateTime d1Latest = OffsetDateTime.of(2026, 6, 30, 23, 59, 59, 0, ZoneOffset.UTC);
        stub(Timeframe.D1, OffsetDateTime.of(2025, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC), d1Latest, 45);
        stubEmpty(Timeframe.W1);

        Map<String, CandleCoverageDto> coverage = service.getCoverage();

        CandleCoverageDto d1 = coverage.get("D1");
        assertThat(d1.expectedLatest()).isEqualTo(boundary.minusDays(1));
        assertThat(d1.upToDate()).isTrue();
        assertThat(d1.assetCount()).isEqualTo(45);
    }

    @Test
    void reportsStaleWhenLatestIsBehindExpected() {
        // latest is 3 days old → before expectedLatest (yesterday) → stale
        OffsetDateTime d1Latest = OffsetDateTime.of(2026, 6, 28, 23, 59, 59, 0, ZoneOffset.UTC);
        stub(Timeframe.D1, OffsetDateTime.of(2025, 7, 1, 0, 0, 0, 0, ZoneOffset.UTC), d1Latest, 45);
        stubEmpty(Timeframe.W1);

        CandleCoverageDto d1 = service.getCoverage().get("D1");

        assertThat(d1.upToDate()).isFalse();
        assertThat(d1.latest()).isBefore(d1.expectedLatest());
    }

    @Test
    void emptyTimeframeIsPresentWithNullsAndNotUpToDate() {
        stubEmpty(Timeframe.D1);
        stubEmpty(Timeframe.W1);

        Map<String, CandleCoverageDto> coverage = service.getCoverage();

        assertThat(coverage).containsKeys("D1", "W1");
        CandleCoverageDto w1 = coverage.get("W1");
        assertThat(w1.earliest()).isNull();
        assertThat(w1.latest()).isNull();
        assertThat(w1.assetCount()).isZero();
        assertThat(w1.upToDate()).isFalse();
        // W1 expectedLatest = boundary - 7 days
        assertThat(w1.expectedLatest()).isEqualTo(boundary.minusDays(7));
    }

    private void stub(Timeframe tf, OffsetDateTime earliest, OffsetDateTime latest, long assetCount) {
        when(candleRepository.findEarliestCloseTimeForTimeframe(eq(tf.name()))).thenReturn(Optional.of(earliest));
        when(candleRepository.findLatestCloseTimeForTimeframe(eq(tf.name()))).thenReturn(Optional.of(latest));
        when(candleRepository.countDistinctAssetsForTimeframe(eq(tf.name()))).thenReturn(assetCount);
    }

    private void stubEmpty(Timeframe tf) {
        lenient().when(candleRepository.findEarliestCloseTimeForTimeframe(eq(tf.name()))).thenReturn(Optional.empty());
        lenient().when(candleRepository.findLatestCloseTimeForTimeframe(eq(tf.name()))).thenReturn(Optional.empty());
        lenient().when(candleRepository.countDistinctAssetsForTimeframe(eq(tf.name()))).thenReturn(0L);
    }
}
