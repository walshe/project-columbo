package walshe.projectcolumbo.persistence.repository;

import java.math.BigDecimal;

public interface AssetClosePrice {
    Long getAssetId();
    BigDecimal getClose();
}
