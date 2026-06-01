package walshe.projectcolumbo.persistence.repository;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.entity.MacdIndicator;
import walshe.projectcolumbo.persistence.entity.Asset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface MacdRepository extends JpaRepository<MacdIndicator, Long> {

    Optional<MacdIndicator> findFirstByAssetAndTimeframeOrderByCloseTimeDesc(
            Asset asset, Timeframe timeframe);

    Optional<MacdIndicator> findByAssetAndTimeframeAndCloseTime(
            Asset asset, Timeframe timeframe, OffsetDateTime closeTime);

    List<MacdIndicator> findByAssetAndTimeframeOrderByCloseTimeAsc(
            Asset asset, Timeframe timeframe);
}
