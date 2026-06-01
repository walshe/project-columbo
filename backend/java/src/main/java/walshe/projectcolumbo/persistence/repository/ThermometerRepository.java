package walshe.projectcolumbo.persistence.repository;

import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.ThermometerIndicator;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ThermometerRepository extends JpaRepository<ThermometerIndicator, Long> {

    Optional<ThermometerIndicator> findFirstByAssetOrderByCloseTimeDesc(Asset asset);

    Optional<ThermometerIndicator> findByAssetAndCloseTime(Asset asset, OffsetDateTime closeTime);

    List<ThermometerIndicator> findByAssetOrderByCloseTimeAsc(Asset asset);
}
