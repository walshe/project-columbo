package walshe.projectcolumbo.api.v1.scan;

import org.springframework.stereotype.Component;
import walshe.projectcolumbo.api.exception.BadRequestException;
import walshe.projectcolumbo.api.v1.scan.dto.ScanCondition;
import walshe.projectcolumbo.api.v1.scan.dto.ScanRequest;
import walshe.projectcolumbo.persistence.IndicatorType;
import walshe.projectcolumbo.persistence.SignalEvent;
import walshe.projectcolumbo.persistence.TrendState;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Component
public class ScanValidator {

    private static final Map<IndicatorType, Set<SignalEvent>> VALID_EVENTS = new EnumMap<>(IndicatorType.class);
    private static final Map<IndicatorType, Set<TrendState>> VALID_STATES = new EnumMap<>(IndicatorType.class);

    static {
        VALID_EVENTS.put(IndicatorType.SUPERTREND, Set.of(SignalEvent.BULLISH_REVERSAL, SignalEvent.BEARISH_REVERSAL));
        VALID_EVENTS.put(IndicatorType.RSI, Set.of(SignalEvent.CROSSED_ABOVE_60, SignalEvent.CROSSED_BELOW_40));

        VALID_STATES.put(IndicatorType.SUPERTREND, Set.of(TrendState.BULLISH, TrendState.BEARISH));
        VALID_STATES.put(IndicatorType.RSI, Set.of(TrendState.ABOVE_60, TrendState.BELOW_40, TrendState.NEUTRAL));
    }

    void validate(ScanRequest request) {
        if (request.conditions() == null || request.conditions().isEmpty()) {
            throw new BadRequestException("At least one scan condition must be provided");
        }

        for (ScanCondition condition : request.conditions()) {
            validateCondition(condition);
        }
    }

    private void validateCondition(ScanCondition condition) {
        IndicatorType type = condition.indicatorType();
        SignalEvent event = condition.event();
        TrendState state = condition.state();
        Integer maxDaysSinceFlip = condition.maxDaysSinceFlip();
        Integer maxDaysSinceCross = condition.maxDaysSinceCross();

        if (event == null && state == null) {
            throw new BadRequestException(String.format("Either event or state must be provided for indicator %s", type));
        }

        if (event != null) {
            Set<SignalEvent> allowedEvents = VALID_EVENTS.get(type);
            if (allowedEvents == null || !allowedEvents.contains(event)) {
                throw new BadRequestException(String.format("Event %s is not valid for indicator %s", event, type));
            }

            if (maxDaysSinceCross != null && type != IndicatorType.RSI) {
                throw new BadRequestException("maxDaysSinceCross can only be used with indicator RSI");
            }

            if (maxDaysSinceCross != null && (event != SignalEvent.CROSSED_ABOVE_60 && event != SignalEvent.CROSSED_BELOW_40)) {
                throw new BadRequestException("maxDaysSinceCross can only be used with RSI CROSSED_ABOVE_60 or CROSSED_BELOW_40");
            }
        }

        if (state != null) {
            Set<TrendState> allowedStates = VALID_STATES.get(type);
            if (allowedStates == null || !allowedStates.contains(state)) {
                throw new BadRequestException(String.format("State %s is not valid for indicator %s", state, type));
            }
        }

        if (maxDaysSinceFlip != null && state == null) {
            throw new BadRequestException("maxDaysSinceFlip can only be used when state is provided");
        }

        if (maxDaysSinceCross != null && event == null) {
            throw new BadRequestException("maxDaysSinceCross can only be used when event is provided");
        }
    }
}
