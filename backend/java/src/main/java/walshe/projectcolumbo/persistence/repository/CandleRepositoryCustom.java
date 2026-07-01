package walshe.projectcolumbo.persistence.repository;
import walshe.projectcolumbo.persistence.model.Timeframe;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public interface CandleRepositoryCustom {
    List<AssetCloseAtTime> findClosePricesAtFlipTimes(Timeframe timeframe, Map<Long, OffsetDateTime> flipCloseTimeByAssetId);
}
