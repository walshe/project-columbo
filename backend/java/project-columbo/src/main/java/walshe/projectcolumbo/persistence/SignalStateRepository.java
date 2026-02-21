package walshe.projectcolumbo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface SignalStateRepository extends JpaRepository<SignalState, Long> {

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
