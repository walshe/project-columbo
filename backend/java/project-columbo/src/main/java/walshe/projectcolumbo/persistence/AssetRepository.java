package walshe.projectcolumbo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByActiveTrue();

    interface AssetSummary {
        Long getId();
        String getSymbol();
    }

    List<AssetSummary> findActiveBy();
}
