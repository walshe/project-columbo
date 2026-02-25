package walshe.projectcolumbo.api.v1.dto;

import java.util.List;

public record MarketPulseHistoryDto(
    List<MarketPulseDto> snapshots
) {}
