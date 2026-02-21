package walshe.projectcolumbo.persistence;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
class SignalStateCalculator {

    public List<SignalStateResult> calculate(List<SuperTrendIndicator> indicators) {
        return calculate(indicators, null);
    }

    public List<SignalStateResult> calculate(List<SuperTrendIndicator> indicators, SuperTrendDirection previousDirection) {
        List<SignalStateResult> results = new ArrayList<>();
        SuperTrendDirection lastDirection = previousDirection;

        for (SuperTrendIndicator indicator : indicators) {
            SuperTrendDirection currentDirection = indicator.getDirection();
            TrendState trendState = mapDirection(currentDirection);
            SignalEvent event;

            if (lastDirection == null) {
                // First-row initialization rule
                event = SignalEvent.NONE;
            } else if (lastDirection == SuperTrendDirection.DOWN && currentDirection == SuperTrendDirection.UP) {
                event = SignalEvent.BULLISH_REVERSAL;
            } else if (lastDirection == SuperTrendDirection.UP && currentDirection == SuperTrendDirection.DOWN) {
                event = SignalEvent.BEARISH_REVERSAL;
            } else {
                event = SignalEvent.NONE;
            }

            results.add(new SignalStateResult(indicator.getCloseTime(), trendState, event));
            lastDirection = currentDirection;
        }

        return results;
    }

    private TrendState mapDirection(SuperTrendDirection direction) {
        return direction == SuperTrendDirection.UP ? TrendState.BULLISH : TrendState.BEARISH;
    }
}
