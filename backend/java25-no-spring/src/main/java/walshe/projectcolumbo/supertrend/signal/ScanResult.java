package walshe.projectcolumbo.supertrend.signal;

import java.util.List;
import java.util.Objects;

/** @param matchedConditions one entry per {@link ScanRequest} condition this asset matched, in request order */
public record ScanResult(
        String symbol,
        List<ScanConditionMatch> matchedConditions
) {
    public ScanResult {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(matchedConditions, "matchedConditions must not be null");
    }
}
