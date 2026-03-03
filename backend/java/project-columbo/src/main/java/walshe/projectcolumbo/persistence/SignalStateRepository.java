package walshe.projectcolumbo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface SignalStateRepository extends JpaRepository<SignalState, Long>, SignalStateRepositoryCustom {

    @Query("""
           SELECT s FROM SignalState s
           JOIN FETCH s.asset a
           WHERE a.active = true
             AND s.timeframe = :timeframe
             AND s.indicatorType = :indicatorType
             AND s.event = :event
             AND s.closeTime = (
                 SELECT MAX(s2.closeTime)
                 FROM SignalState s2
                 WHERE s2.timeframe = :timeframe
             )
           """)
    List<SignalState> findLatestByCondition(
            @Param("timeframe") Timeframe timeframe,
            @Param("indicatorType") IndicatorType indicatorType,
            @Param("event") SignalEvent event
    );

    @Query("""
           SELECT s FROM SignalState s
           JOIN FETCH s.asset a
           WHERE a.active = true
             AND s.timeframe = :timeframe
             AND s.indicatorType = :indicatorType
             AND s.closeTime < :boundary
             AND s.closeTime = (
                 SELECT MAX(s2.closeTime)
                 FROM SignalState s2
                 WHERE s2.asset.id = s.asset.id
                   AND s2.timeframe = :timeframe
                   AND s2.indicatorType = :indicatorType
                   AND s2.closeTime < :boundary
             )
           """)
    List<SignalState> findLatestFinalizedForActiveAssets(
            @Param("timeframe") Timeframe timeframe,
            @Param("indicatorType") IndicatorType indicatorType,
            @Param("boundary") OffsetDateTime boundary
    );

    @Query("""
           SELECT s FROM SignalState s
           JOIN FETCH s.asset a
           WHERE a.active = true
             AND s.timeframe = :timeframe
             AND s.indicatorType = :indicatorType
             AND s.event != 'NONE'
             AND s.closeTime < :boundary
             AND s.closeTime = (
                 SELECT MAX(s2.closeTime)
                 FROM SignalState s2
                 WHERE s2.asset.id = s.asset.id
                   AND s2.timeframe = :timeframe
                   AND s2.indicatorType = :indicatorType
                   AND s2.event != 'NONE'
                   AND s2.closeTime < :boundary
             )
           """)
    List<SignalState> findLatestFinalizedFlipsForActiveAssets(
            @Param("timeframe") Timeframe timeframe,
            @Param("indicatorType") IndicatorType indicatorType,
            @Param("boundary") OffsetDateTime boundary
    );

    @Query("""
           SELECT s FROM SignalState s
           JOIN FETCH s.asset a
           WHERE a.active = true
             AND s.timeframe = :timeframe
             AND s.indicatorType = :indicatorType
             AND s.closeTime < :boundary
             AND s.closeTime = (
                 SELECT MIN(s2.closeTime)
                 FROM SignalState s2
                 WHERE s2.asset.id = s.asset.id
                   AND s2.timeframe = :timeframe
                   AND s2.indicatorType = :indicatorType
                   AND s2.closeTime < :boundary
             )
           """)
    List<SignalState> findEarliestFinalizedForActiveAssets(
            @Param("timeframe") Timeframe timeframe,
            @Param("indicatorType") IndicatorType indicatorType,
            @Param("boundary") OffsetDateTime boundary
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
    @Query("""
           SELECT MAX(s.closeTime)
           FROM SignalState s
           WHERE s.asset.id = :assetId
             AND s.timeframe = :timeframe
             AND s.indicatorType = :indicatorType
             AND s.trendState != :currentState
             AND s.closeTime < :latestTime
           """)
    Optional<OffsetDateTime> findLastDifferentStateTime(
            @Param("assetId") Long assetId,
            @Param("timeframe") Timeframe timeframe,
            @Param("indicatorType") IndicatorType indicatorType,
            @Param("currentState") TrendState currentState,
            @Param("latestTime") OffsetDateTime latestTime
    );

    @Query("""
           SELECT MIN(s.closeTime)
           FROM SignalState s
           WHERE s.asset.id = :assetId
             AND s.timeframe = :timeframe
             AND s.indicatorType = :indicatorType
             AND s.trendState = :currentState
             AND s.closeTime > :afterTime
             AND s.closeTime <= :latestTime
           """)
    Optional<OffsetDateTime> findFirstCurrentStateTimeAfter(
            @Param("assetId") Long assetId,
            @Param("timeframe") Timeframe timeframe,
            @Param("indicatorType") IndicatorType indicatorType,
            @Param("currentState") TrendState currentState,
            @Param("afterTime") OffsetDateTime afterTime,
            @Param("latestTime") OffsetDateTime latestTime
    );
}
