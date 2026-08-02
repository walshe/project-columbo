package walshe.projectcolumbo.supertrend.signal;

import java.util.List;

/** @param matchedConditions one entry per {@link ScanRequest} condition this asset matched, in request order */
public record ScanResult(
        String symbol,
        List<ScanConditionMatch> matchedConditions
) {
}
