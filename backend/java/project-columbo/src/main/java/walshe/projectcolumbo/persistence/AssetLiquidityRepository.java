package walshe.projectcolumbo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetLiquidityRepository extends JpaRepository<AssetLiquidityView, Long> {
}
