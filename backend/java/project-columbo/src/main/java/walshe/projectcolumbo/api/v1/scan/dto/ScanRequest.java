package walshe.projectcolumbo.api.v1.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import walshe.projectcolumbo.persistence.model.Timeframe;

import java.util.List;

@Schema(description = "Request to scan for assets matching specific indicator conditions")
public record ScanRequest(
    @NotNull(message = "timeframe is required")
    @Schema(description = "The timeframe to evaluate", example = "D1")
    Timeframe timeframe,

    @NotNull(message = "operator is required")
    @Schema(description = "The logical operator to combine conditions", example = "AND")
    ScanOperator operator,

    @NotEmpty(message = "At least one condition must be provided")
    @Valid
    @Schema(description = "The list of conditions to match")
    List<ScanCondition> conditions,

    @Schema(description = "Maximum number of results to return", example = "50")
    Integer limit
) {}
