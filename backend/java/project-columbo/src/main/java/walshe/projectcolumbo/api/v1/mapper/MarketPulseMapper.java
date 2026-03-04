package walshe.projectcolumbo.api.v1.mapper;

import walshe.projectcolumbo.api.v1.dto.MarketPulseDto;
import walshe.projectcolumbo.persistence.entity.MarketBreadthSnapshot;

public class MarketPulseMapper {
    public static MarketPulseDto toDto(MarketBreadthSnapshot snapshot) {
        return new MarketPulseDto(
            snapshot.getSnapshotCloseTime(),
            snapshot.getBullishCount(),
            snapshot.getBearishCount(),
            snapshot.getMissingCount(),
            snapshot.getTotalAssets(),
            snapshot.getBullishRatio()
        );
    }
}
