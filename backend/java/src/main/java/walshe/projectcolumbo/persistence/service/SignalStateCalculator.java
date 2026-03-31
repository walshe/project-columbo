package walshe.projectcolumbo.persistence.service;
import walshe.projectcolumbo.persistence.model.SignalStateResult;
import walshe.projectcolumbo.persistence.model.SignalEvent;
import walshe.projectcolumbo.persistence.model.SuperTrendDirection;
import walshe.projectcolumbo.persistence.model.TrendState;
import walshe.projectcolumbo.persistence.entity.RsiIndicator;
import walshe.projectcolumbo.persistence.entity.SuperTrendIndicator;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
class SignalStateCalculator {

    /**
     * Entry point to calculate trend signals (like Bullish or Bearish) from a list of indicator results.
     * This version starts fresh with no prior trend information.
     */
    public List<SignalStateResult> calculate(List<SuperTrendIndicator> indicators) {
        return calculate(indicators, null);
    }

    /**
     * Calculates the current trend and identifies any "flips" (reversals) for a series of data points.
     * 
     * @param indicators The list of technical indicator data to process (like SuperTrend values).
     * @param previousDirection The last known trend direction from a previous run (if any).
     * @return A list of results containing the trend state and any specific events (like Reversals).
     */
    public List<SignalStateResult> calculate(List<SuperTrendIndicator> indicators, SuperTrendDirection previousDirection) {
        List<SignalStateResult> results = new ArrayList<>();
        // Keep track of the "last" direction as we move through the list to spot changes
        SuperTrendDirection lastDirection = previousDirection;

        for (SuperTrendIndicator indicator : indicators) {
            SuperTrendDirection currentDirection = indicator.getDirection();
            // Convert the technical direction (UP/DOWN) into a trend name (BULLISH/BEARISH)
            TrendState trendState = mapDirection(currentDirection);
            SignalEvent event;

            if (lastDirection == null) {
                // If this is the very first piece of data we have, we don't know yet if a change happened.
                event = SignalEvent.NONE;
            } else if (lastDirection == SuperTrendDirection.DOWN && currentDirection == SuperTrendDirection.UP) {
                // The price was trending down but is now trending up — a "Bullish Reversal"
                event = SignalEvent.BULLISH_REVERSAL;
            } else if (lastDirection == SuperTrendDirection.UP && currentDirection == SuperTrendDirection.DOWN) {
                // The price was trending up but is now trending down — a "Bearish Reversal"
                event = SignalEvent.BEARISH_REVERSAL;
            } else {
                // No change in direction; the current trend is simply continuing.
                event = SignalEvent.NONE;
            }

            // Record the findings for this specific point in time
            results.add(new SignalStateResult(indicator.getCloseTime(), trendState, event));
            
            // Update the "last direction" so we can compare it with the next data point in the loop
            lastDirection = currentDirection;
        }

        return results;
    }

    /**
     * Simple helper to translate technical SuperTrend directions into user-friendly trend names.
     */
    private TrendState mapDirection(SuperTrendDirection direction) {
        return direction == SuperTrendDirection.UP ? TrendState.BULLISH : TrendState.BEARISH;
    }
    /**
     * Calculates the RSI trend states and events.
     */
    public List<SignalStateResult> calculateRsi(List<RsiIndicator> indicators, TrendState previousTrend) {
        List<SignalStateResult> results = new ArrayList<>();
        TrendState lastTrend = previousTrend;

        for (RsiIndicator indicator : indicators) {
            BigDecimal rsi = indicator.getRsiValue();
            TrendState currentTrend;
            
            if (rsi.compareTo(BigDecimal.valueOf(60)) >= 0) {
                currentTrend = TrendState.ABOVE_60;
            } else if (rsi.compareTo(BigDecimal.valueOf(40)) <= 0) {
                currentTrend = TrendState.BELOW_40;
            } else {
                currentTrend = TrendState.NEUTRAL;
            }

            SignalEvent event = SignalEvent.NONE;
            if (lastTrend != null && lastTrend != currentTrend) {
                if (currentTrend == TrendState.ABOVE_60) {
                    event = SignalEvent.CROSSED_ABOVE_60;
                } else if (currentTrend == TrendState.BELOW_40) {
                    event = SignalEvent.CROSSED_BELOW_40;
                }
            }

            results.add(new SignalStateResult(indicator.getCloseTime(), currentTrend, event));
            lastTrend = currentTrend;
        }

        return results;
    }
}
