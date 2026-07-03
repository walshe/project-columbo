package walshe.projectcolumbo.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "A time-ordered series of market breadth snapshots")
public record MarketPulseHistoryDto(
    @Schema(description = "Breadth snapshots ordered by snapshot close time (oldest to newest)")
    List<MarketPulseDto> snapshots
) {}
