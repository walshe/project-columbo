package walshe.projectcolumbo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SuperTrendRepository extends JpaRepository<SuperTrendIndicator, Long> {
    Optional<SuperTrendIndicator> findFirstByAssetIdAndTimeframeOrderByCloseTimeDesc(Long assetId, Timeframe timeframe);
}
