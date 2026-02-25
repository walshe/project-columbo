package walshe.projectcolumbo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface SignalStateRepository extends JpaRepository<SignalState, Long> {

    @Query("""
           SELECT s FROM SignalState s
           JOIN FETCH s.asset a
           WHERE a.active = true
             AND s.timeframe = :timeframe
             AND s.indicatorType = :indicatorType
             AND s.closeTime = (
                 SELECT MAX(s2.closeTime)
                 FROM SignalState s2
                 WHERE s2.asset.id = s.asset.id
                   AND s2.timeframe = :timeframe
                   AND s2.indicatorType = :indicatorType
             )
           """)
    List<SignalState> findLatestForActiveAssets(
            @Param("timeframe") Timeframe timeframe,
            @Param("indicatorType") IndicatorType indicatorType
    );

    @Query("""
           SELECT s FROM SignalState s
           JOIN FETCH s.asset a
           WHERE a.active = true
             AND s.timeframe = :timeframe
             AND s.indicatorType = :indicatorType
             AND s.event != 'NONE'
             AND s.closeTime = (
                 SELECT MAX(s2.closeTime)
                 FROM SignalState s2
                 WHERE s2.asset.id = s.asset.id
                   AND s2.timeframe = :timeframe
                   AND s2.indicatorType = :indicatorType
                   AND s2.event != 'NONE'
             )
           """)
    List<SignalState> findLatestFlipsForActiveAssets(
            @Param("timeframe") Timeframe timeframe,
            @Param("indicatorType") IndicatorType indicatorType
    );

    Optional<SignalState> findFirstByAssetIdAndTimeframeAndIndicatorTypeOrderByCloseTimeDesc(
            Long assetId,
            Timeframe timeframe,
            IndicatorType indicatorType
    );

    List<SignalState> findAllByAssetIdAndTimeframeAndIndicatorTypeOrderByCloseTimeAsc(
            Long assetId,
            Timeframe timeframe,
            IndicatorType indicatorType
    );

    Optional<SignalState> findByAssetAndTimeframeAndIndicatorTypeAndCloseTime(
            Asset asset,
            Timeframe timeframe,
            IndicatorType indicatorType,
            OffsetDateTime closeTime
    );
}
