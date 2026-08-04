package walshe.projectcolumbo.supertrend.signal;

import java.util.List;
import java.util.Objects;

/** @param limit optional; caps the number of returned matches when set */
public record ScanRequest(
        ScanOperator operator,
        List<ScanCondition> conditions,
        Integer limit
) {
    public ScanRequest {
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(conditions, "conditions must not be null");
        if (limit != null && limit < 0) {
            throw new IllegalArgumentException("limit must not be negative, was: " + limit);
        }
    }
}
