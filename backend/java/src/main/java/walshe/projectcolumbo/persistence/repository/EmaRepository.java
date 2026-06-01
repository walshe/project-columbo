package walshe.projectcolumbo.persistence.repository;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.entity.EmaIndicator;
import walshe.projectcolumbo.persistence.entity.Asset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface EmaRepository extends JpaRepository<EmaIndicator, Long> {

    Optional<EmaIndicator> findFirstByAssetAndTimeframeAndPeriodOrderByCloseTimeDesc(
            Asset asset, Timeframe timeframe, int period);

    Optional<EmaIndicator> findByAssetAndTimeframeAndPeriodAndCloseTime(
            Asset asset, Timeframe timeframe, int period, OffsetDateTime closeTime);

    List<EmaIndicator> findByAssetAndTimeframeAndPeriodOrderByCloseTimeAsc(
            Asset asset, Timeframe timeframe, int period);
}
