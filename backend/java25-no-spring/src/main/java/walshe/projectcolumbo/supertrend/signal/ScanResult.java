package walshe.projectcolumbo.supertrend.signal;

import walshe.projectcolumbo.supertrend.shared.AssetClass;

import java.util.List;
import java.util.Objects;

/** @param matchedConditions one entry per {@link ScanRequest} condition this asset matched, in request order */
public record ScanResult(
        String symbol,
        AssetClass assetClass,
        List<ScanConditionMatch> matchedConditions
) {
    public ScanResult {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(assetClass, "assetClass must not be null");
        Objects.requireNonNull(matchedConditions, "matchedConditions must not be null");
    }
}
