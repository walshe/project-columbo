package walshe.projectcolumbo.api.v1.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "How multiple scan conditions are combined: AND requires all to match on the same "
        + "asset; OR requires any.")
public enum ScanOperator {
    AND,
    OR
}
