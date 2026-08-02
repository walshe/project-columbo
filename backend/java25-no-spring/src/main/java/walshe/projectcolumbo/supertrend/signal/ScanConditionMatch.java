package walshe.projectcolumbo.supertrend.signal;

import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.time.OffsetDateTime;

/** Why a matched asset satisfied one particular {@link ScanCondition}. */
public record ScanConditionMatch(
        Timeframe timeframe,
        TrendState state,
        OffsetDateTime lastFlipTime,
        Long daysSinceFlip
) {
}
