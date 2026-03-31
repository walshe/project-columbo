package walshe.projectcolumbo.persistence.repository;
import walshe.projectcolumbo.persistence.model.SignalEvent;
import walshe.projectcolumbo.persistence.model.TrendState;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.entity.SignalState;

import java.time.OffsetDateTime;
import java.util.List;

public interface SignalStateRepositoryCustom {
    List<SignalState> findEventMatches(IndicatorType indicatorType, SignalEvent event, Timeframe timeframe, OffsetDateTime latestCloseTime, Integer maxDaysSinceCross);
    List<SignalState> findStateMatches(IndicatorType indicatorType, TrendState state, Timeframe timeframe, Integer maxDaysSinceFlip);
}
