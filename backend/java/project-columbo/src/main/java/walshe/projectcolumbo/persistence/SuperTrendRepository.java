package walshe.projectcolumbo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SuperTrendRepository extends JpaRepository<SuperTrendIndicator, Long> {
    Optional<SuperTrendIndicator> findFirstByAssetAndTimeframeOrderByCloseTimeDesc(Asset asset, Timeframe timeframe);
}
