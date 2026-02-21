package walshe.projectcolumbo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface SuperTrendRepository extends JpaRepository<SuperTrendIndicator, Long> {
    Optional<SuperTrendIndicator> findFirstByAssetAndTimeframeOrderByCloseTimeDesc(Asset asset, Timeframe timeframe);

    Optional<SuperTrendIndicator> findByAssetAndTimeframeAndCloseTime(Asset asset, Timeframe timeframe, OffsetDateTime closeTime);

    List<SuperTrendIndicator> findByAssetAndTimeframeAndCloseTimeAfterOrderByCloseTimeAsc(Asset asset, Timeframe timeframe, OffsetDateTime closeTime);

    List<SuperTrendIndicator> findByAssetAndTimeframeOrderByCloseTimeAsc(Asset asset, Timeframe timeframe);
}
