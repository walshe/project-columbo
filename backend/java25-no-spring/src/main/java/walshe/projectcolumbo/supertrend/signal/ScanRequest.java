package walshe.projectcolumbo.supertrend.signal;

import java.util.List;

/** @param limit optional; caps the number of returned matches when set */
public record ScanRequest(
        ScanOperator operator,
        List<ScanCondition> conditions,
        Integer limit
) {
}
