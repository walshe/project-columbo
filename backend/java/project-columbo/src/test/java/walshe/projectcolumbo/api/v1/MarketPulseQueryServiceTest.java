package walshe.projectcolumbo.api.v1;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import walshe.projectcolumbo.api.v1.dto.MarketPulseDto;
import walshe.projectcolumbo.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketPulseQueryServiceTest {

    @Mock
    private MarketBreadthSnapshotRepository repository;

    @InjectMocks
    private MarketPulseQueryService service;

    private final OffsetDateTime time = OffsetDateTime.of(2024, 1, 10, 0, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void shouldGetLatestPulse() {
        MarketBreadthSnapshot snapshot = new MarketBreadthSnapshot(
                Timeframe.D1, IndicatorType.SUPERTREND, time, 70, 20, 10, 100, new BigDecimal("0.70")
        );

        when(repository.findTopByTimeframeAndIndicatorTypeOrderBySnapshotCloseTimeDesc(Timeframe.D1, IndicatorType.SUPERTREND))
                .thenReturn(Optional.of(snapshot));

        Optional<MarketPulseDto> result = service.getLatestPulse(Timeframe.D1, IndicatorType.SUPERTREND);

        assertThat(result).isPresent();
        assertThat(result.get().bullishRatio()).isEqualByComparingTo("0.70");
    }

    @Test
    void shouldGetPulseHistory() {
        MarketBreadthSnapshot s1 = new MarketBreadthSnapshot(
                Timeframe.D1, IndicatorType.SUPERTREND, time.minusDays(1), 60, 30, 10, 100, new BigDecimal("0.60")
        );
        MarketBreadthSnapshot s2 = new MarketBreadthSnapshot(
                Timeframe.D1, IndicatorType.SUPERTREND, time, 70, 20, 10, 100, new BigDecimal("0.70")
        );

        when(repository.findByTimeframeAndIndicatorTypeAndSnapshotCloseTimeBetweenOrderBySnapshotCloseTimeAsc(
                eq(Timeframe.D1), eq(IndicatorType.SUPERTREND), any(), any()))
                .thenReturn(List.of(s1, s2));

        List<MarketPulseDto> result = service.getPulseHistory(Timeframe.D1, IndicatorType.SUPERTREND, null, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).bullishRatio()).isEqualByComparingTo("0.60");
        assertThat(result.get(1).bullishRatio()).isEqualByComparingTo("0.70");
    }
}
