package walshe.projectcolumbo.api.v1.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import walshe.projectcolumbo.persistence.Timeframe;

import java.util.List;

@Schema(description = "Response containing the results of a market scan")
public record ScanResponse(
    @Schema(description = "The timeframe evaluated")
    Timeframe timeframe,

    @Schema(description = "The logical operator used to combine conditions")
    ScanOperator operator,

    @Schema(description = "The original conditions evaluated")
    List<ScanCondition> conditions,

    @Schema(description = "The list of assets matching the scan conditions")
    List<ScanResult> results
) {}
