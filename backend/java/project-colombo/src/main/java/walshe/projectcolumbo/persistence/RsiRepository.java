package walshe.projectcolumbo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

interface RsiRepository extends JpaRepository<RsiIndicator, Long> {
    Optional<RsiIndicator> findFirstByAssetAndTimeframeOrderByCloseTimeDesc(Asset asset, Timeframe timeframe);

    Optional<RsiIndicator> findByAssetAndTimeframeAndCloseTime(Asset asset, Timeframe timeframe, OffsetDateTime closeTime);
}
