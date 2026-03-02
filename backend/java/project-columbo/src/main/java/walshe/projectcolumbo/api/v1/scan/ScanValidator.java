package walshe.projectcolumbo.api.v1.scan;

import org.springframework.stereotype.Component;
import walshe.projectcolumbo.api.exception.BadRequestException;
import walshe.projectcolumbo.api.v1.scan.dto.ScanCondition;
import walshe.projectcolumbo.api.v1.scan.dto.ScanRequest;
import walshe.projectcolumbo.persistence.IndicatorType;
import walshe.projectcolumbo.persistence.SignalEvent;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Component
class ScanValidator {

    private static final Map<IndicatorType, Set<SignalEvent>> VALID_EVENTS = new EnumMap<>(IndicatorType.class);

    static {
        VALID_EVENTS.put(IndicatorType.SUPERTREND, Set.of(SignalEvent.BULLISH_REVERSAL, SignalEvent.BEARISH_REVERSAL));
        VALID_EVENTS.put(IndicatorType.RSI, Set.of(SignalEvent.CROSSED_ABOVE_60, SignalEvent.CROSSED_BELOW_40));
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

        Set<SignalEvent> allowedEvents = VALID_EVENTS.get(type);
        if (allowedEvents == null || !allowedEvents.contains(event)) {
            throw new BadRequestException(String.format("Event %s is not valid for indicator %s", event, type));
        }
    }
}
