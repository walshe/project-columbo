package walshe.projectcolumbo.persistence.repository;
import walshe.projectcolumbo.persistence.entity.AssetLiquidityView;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetLiquidityRepository extends JpaRepository<AssetLiquidityView, Long> {
}
