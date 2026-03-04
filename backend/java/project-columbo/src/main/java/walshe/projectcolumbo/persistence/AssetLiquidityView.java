package walshe.projectcolumbo.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

@Entity
@Immutable
@Table(name = "v_asset_liquidity")
public class AssetLiquidityView {

    @Id
    private Long assetId;

    @Column(name = "avg_volume_7d")
    private BigDecimal avgVolume7d;

    public AssetLiquidityView() {
    }

    public Long getAssetId() {
        return assetId;
    }

    public BigDecimal getAvgVolume7d() {
        return avgVolume7d;
    }
}
