package walshe.projectcolumbo.persistence.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface AssetCloseAtTime {
    Long getAssetId();
    OffsetDateTime getCloseTime();
    BigDecimal getClose();
}
