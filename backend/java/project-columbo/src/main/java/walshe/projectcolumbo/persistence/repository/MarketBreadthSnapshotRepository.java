package walshe.projectcolumbo.persistence.repository;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.entity.MarketBreadthSnapshot;

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

    Optional<MarketBreadthSnapshot> findByTimeframeAndIndicatorTypeAndSnapshotCloseTime(
            Timeframe timeframe, IndicatorType indicatorType, OffsetDateTime snapshotCloseTime);
}
