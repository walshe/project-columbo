package walshe.projectcolumbo.persistence;

import java.time.OffsetDateTime;
import java.util.List;

public interface SignalStateRepositoryCustom {
    List<SignalState> findEventMatches(IndicatorType indicatorType, SignalEvent event, Timeframe timeframe, OffsetDateTime latestCloseTime, Integer maxDaysSinceCross);
    List<SignalState> findStateMatches(IndicatorType indicatorType, TrendState state, Timeframe timeframe, Integer maxDaysSinceFlip);
}
