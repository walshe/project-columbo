package walshe.projectcolumbo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface MarketBreadthSnapshotRepository extends JpaRepository<MarketBreadthSnapshot, Long> {

    Optional<MarketBreadthSnapshot> findTopByTimeframeAndIndicatorTypeOrderBySnapshotCloseTimeDesc(
            Timeframe timeframe, IndicatorType indicatorType);

    List<MarketBreadthSnapshot> findByTimeframeAndIndicatorTypeAndSnapshotCloseTimeBetweenOrderBySnapshotCloseTimeAsc(
            Timeframe timeframe, IndicatorType indicatorType, OffsetDateTime start, OffsetDateTime end);

    List<MarketBreadthSnapshot> findByTimeframeAndIndicatorTypeOrderBySnapshotCloseTimeAsc(
            Timeframe timeframe, IndicatorType indicatorType);
}
