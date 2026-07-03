package walshe.projectcolumbo.api.v1.scan;

import org.springframework.stereotype.Component;
import walshe.projectcolumbo.api.exception.BadRequestException;
import walshe.projectcolumbo.api.v1.scan.dto.ScanCondition;
import walshe.projectcolumbo.api.v1.scan.dto.ScanRequest;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.SignalEvent;
import walshe.projectcolumbo.persistence.model.TrendState;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Component
public class ScanValidator {

    private static final Map<IndicatorType, Set<SignalEvent>> VALID_EVENTS = new EnumMap<>(IndicatorType.class);
    private static final Map<IndicatorType, Set<TrendState>> VALID_STATES = new EnumMap<>(IndicatorType.class);

    static {
        VALID_EVENTS.put(IndicatorType.SUPERTREND, Set.of(SignalEvent.SUPERTREND_BULLISH_REVERSAL, SignalEvent.SUPERTREND_BEARISH_REVERSAL));
        VALID_EVENTS.put(IndicatorType.RSI, Set.of(SignalEvent.RSI_CROSSED_ABOVE_60, SignalEvent.RSI_CROSSED_BELOW_40));

        VALID_STATES.put(IndicatorType.SUPERTREND, Set.of(TrendState.SUPERTREND_BULLISH, TrendState.SUPERTREND_BEARISH));
        VALID_STATES.put(IndicatorType.RSI, Set.of(TrendState.RSI_ABOVE_60, TrendState.RSI_BELOW_40, TrendState.RSI_NEUTRAL));

        // DISABLED: Elder Impulse System / Market Thermometer are not computed by the pipeline,
        // so scan conditions on them are rejected up front in validateCondition(). Restore these
        // entries (and remove the guard) to re-enable Elder scans.
        // VALID_EVENTS.put(IndicatorType.ELDER_IMPULSE,
        //         Set.of(SignalEvent.ELDER_IMPULSE_TURNED_GREEN, SignalEvent.ELDER_IMPULSE_TURNED_RED, SignalEvent.ELDER_IMPULSE_TURNED_NEUTRAL));
        //
        // VALID_STATES.put(IndicatorType.ELDER_IMPULSE,
        //         Set.of(TrendState.ELDER_IMPULSE_GREEN, TrendState.ELDER_IMPULSE_RED, TrendState.ELDER_IMPULSE_NEUTRAL));
        //
        // VALID_EVENTS.put(IndicatorType.ELDER_THERMOMETER,
        //         Set.of(SignalEvent.ELDER_THERMOMETER_CROSSED_ABOVE_EMA,
        //                SignalEvent.ELDER_THERMOMETER_CROSSED_BELOW_EMA,
        //                SignalEvent.ELDER_THERMOMETER_TRIPLE_SPIKE));
        //
        // VALID_STATES.put(IndicatorType.ELDER_THERMOMETER,
        //         Set.of(TrendState.ELDER_THERMOMETER_QUIET, TrendState.ELDER_THERMOMETER_HOT, TrendState.ELDER_THERMOMETER_SPIKE));
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

        // DISABLED: Elder Impulse System / Market Thermometer are not active — reject scans on them.
        if (type == IndicatorType.ELDER_IMPULSE || type == IndicatorType.ELDER_THERMOMETER) {
            throw new BadRequestException(String.format(
                    "Indicator %s is currently disabled (Elder Impulse System not active)", type));
        }

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

            if (maxDaysSinceCross != null && (event != SignalEvent.RSI_CROSSED_ABOVE_60 && event != SignalEvent.RSI_CROSSED_BELOW_40)) {
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
