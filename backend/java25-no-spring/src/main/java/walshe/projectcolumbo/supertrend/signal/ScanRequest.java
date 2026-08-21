package walshe.projectcolumbo.supertrend.signal;

import walshe.projectcolumbo.supertrend.shared.AssetClass;

import java.util.List;
import java.util.Objects;

/**
 * @param operator   AND = an asset must match every condition to be returned; OR = matching any one condition is enough
 * @param conditions one or more {@link ScanCondition}s, each independently checked against every candidate asset - conditions
 *                   can target different timeframes (e.g. one D1 condition AND one W1 condition on the same request)
 * @param limit      optional; caps the number of returned matches when set
 * @param assetClass optional; restricts every condition's candidates to this asset class when set
 * @param sort       optional; defaults to {@link ScanSort#SYMBOL_ASC} when null
 */
public record ScanRequest(
        ScanOperator operator,
        List<ScanCondition> conditions,
        Integer limit,
        AssetClass assetClass,
        ScanSort sort
) {
    public ScanRequest {
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(conditions, "conditions must not be null");
        if (limit != null && limit < 0) {
            throw new IllegalArgumentException("limit must not be negative, was: " + limit);
        }
    }
}
