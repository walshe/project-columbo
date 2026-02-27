package walshe.projectcolumbo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface RsiRepository extends JpaRepository<RsiIndicator, Long> {
    Optional<RsiIndicator> findFirstByAssetAndTimeframeOrderByCloseTimeDesc(Asset asset, Timeframe timeframe);

    Optional<RsiIndicator> findByAssetAndTimeframeAndCloseTime(Asset asset, Timeframe timeframe, OffsetDateTime closeTime);

    List<RsiIndicator> findByAssetAndTimeframeOrderByCloseTimeAsc(Asset asset, Timeframe timeframe);

    List<RsiIndicator> findByAssetAndTimeframeAndCloseTimeAfterOrderByCloseTimeAsc(Asset asset, Timeframe timeframe, OffsetDateTime closeTime);
}
