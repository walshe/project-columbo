package walshe.projectcolumbo.supertrend.signal;

import walshe.projectcolumbo.supertrend.shared.AssetClass;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * @param matchedConditions one entry per {@link ScanRequest} condition this asset matched, in request order
 * @param avgVolume7d       rolling 7-day average D1 volume for this asset; zero if unavailable - reported once
 *                          per asset here, not duplicated per matched condition
 */
public record ScanResult(
        String symbol,
        AssetClass assetClass,
        List<ScanConditionMatch> matchedConditions,
        BigDecimal avgVolume7d
) {
    public ScanResult {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(assetClass, "assetClass must not be null");
        Objects.requireNonNull(matchedConditions, "matchedConditions must not be null");
        Objects.requireNonNull(avgVolume7d, "avgVolume7d must not be null");
    }
}
