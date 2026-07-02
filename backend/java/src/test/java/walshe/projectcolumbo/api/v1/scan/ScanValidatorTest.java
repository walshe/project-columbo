package walshe.projectcolumbo.api.v1.scan;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.api.exception.BadRequestException;
import walshe.projectcolumbo.api.v1.scan.dto.ScanCondition;
import walshe.projectcolumbo.api.v1.scan.dto.ScanOperator;
import walshe.projectcolumbo.api.v1.scan.dto.ScanRequest;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.SignalEvent;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.TrendState;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScanValidatorTest {

    private final ScanValidator validator = new ScanValidator();

    @Test
    void shouldAcceptValidSuperTrendEventCondition() {
        ScanRequest request = new ScanRequest(
            ScanOperator.AND,
            List.of(new ScanCondition(Timeframe.D1, IndicatorType.SUPERTREND, SignalEvent.SUPERTREND_BULLISH_REVERSAL, null, null, null)),
            null
        );

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    void shouldAcceptValidSuperTrendStateCondition() {
        ScanRequest request = new ScanRequest(
            ScanOperator.AND,
            List.of(new ScanCondition(Timeframe.D1, IndicatorType.SUPERTREND, null, TrendState.SUPERTREND_BULLISH, 5, null)),
            null
        );

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    void shouldAcceptValidRsiEventCondition() {
        ScanRequest request = new ScanRequest(
            ScanOperator.AND,
            List.of(new ScanCondition(Timeframe.D1, IndicatorType.RSI, SignalEvent.RSI_CROSSED_ABOVE_60, null, null, 5)),
            null
        );

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    void shouldAcceptValidRsiStateCondition() {
        ScanRequest request = new ScanRequest(
            ScanOperator.AND,
            List.of(new ScanCondition(Timeframe.D1, IndicatorType.RSI, null, TrendState.RSI_ABOVE_60, null, null)),
            null
        );

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    void shouldRejectEmptyConditions() {
        ScanRequest request = new ScanRequest(
            ScanOperator.AND,
            Collections.emptyList(),
            null
        );

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }

    @Test
    void shouldRejectMissingEventAndState() {
        ScanRequest request = new ScanRequest(
            ScanOperator.AND,
            List.of(new ScanCondition(Timeframe.D1, IndicatorType.SUPERTREND, null, null, null, null)),
            null
        );

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }

    @Test
    void shouldRejectInvalidEventForSuperTrend() {
        ScanRequest request = new ScanRequest(
            ScanOperator.AND,
            List.of(new ScanCondition(Timeframe.D1, IndicatorType.SUPERTREND, SignalEvent.RSI_CROSSED_ABOVE_60, null, null, null)),
            null
        );

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }

    @Test
    void shouldRejectInvalidStateForSuperTrend() {
        ScanRequest request = new ScanRequest(
            ScanOperator.AND,
            List.of(new ScanCondition(Timeframe.D1, IndicatorType.SUPERTREND, null, TrendState.RSI_ABOVE_60, null, null)),
            null
        );

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }

    @Test
    void shouldRejectMaxDaysSinceFlipWithoutState() {
        ScanRequest request = new ScanRequest(
            ScanOperator.AND,
            List.of(new ScanCondition(Timeframe.D1, IndicatorType.SUPERTREND, SignalEvent.SUPERTREND_BULLISH_REVERSAL, null, 5, null)),
            null
        );

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }

    @Test
    void shouldRejectNoneEvent() {
        ScanRequest request = new ScanRequest(
            ScanOperator.AND,
            List.of(new ScanCondition(Timeframe.D1, IndicatorType.SUPERTREND, SignalEvent.NONE, null, null, null)),
            null
        );

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }

    @Test
    void shouldRejectMaxDaysSinceCrossForNonRsiIndicator() {
        ScanRequest request = new ScanRequest(
            ScanOperator.AND,
            List.of(new ScanCondition(Timeframe.D1, IndicatorType.SUPERTREND, SignalEvent.SUPERTREND_BULLISH_REVERSAL, null, null, 5)),
            null
        );

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }

    @Test
    void shouldRejectMaxDaysSinceCrossForNonCrossEvent() {
        ScanRequest request = new ScanRequest(
            ScanOperator.AND,
            List.of(new ScanCondition(Timeframe.D1, IndicatorType.RSI, null, TrendState.RSI_ABOVE_60, null, 5)),
            null
        );

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }

    @Test
    void shouldAcceptConditionWithPerConditionTimeframe() {
        ScanRequest request = new ScanRequest(
            ScanOperator.AND,
            List.of(new ScanCondition(Timeframe.W1, IndicatorType.SUPERTREND, null, TrendState.SUPERTREND_BULLISH, 5, null)),
            null
        );

        assertDoesNotThrow(() -> validator.validate(request));
    }

    // DISABLED: Elder Impulse System / Market Thermometer are not active — scan conditions on
    // them are now rejected. These cases assert the rejection; flip back to assertDoesNotThrow
    // (and restore the ScanValidator entries) to re-enable Elder scans.
    @Test
    void shouldRejectThermometerStateConditionWhileDisabled() {
        ScanRequest request = new ScanRequest(
            ScanOperator.AND,
            List.of(new ScanCondition(Timeframe.D1, IndicatorType.ELDER_THERMOMETER, null,
                    TrendState.ELDER_THERMOMETER_QUIET, null, null)),
            null
        );

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }

    @Test
    void shouldRejectThermometerEventConditionWhileDisabled() {
        ScanRequest request = new ScanRequest(
            ScanOperator.AND,
            List.of(new ScanCondition(Timeframe.D1, IndicatorType.ELDER_THERMOMETER,
                    SignalEvent.ELDER_THERMOMETER_CROSSED_ABOVE_EMA, null, null, null)),
            null
        );

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }

    @Test
    void shouldRejectElderImpulseConditionWhileDisabled() {
        ScanRequest request = new ScanRequest(
            ScanOperator.AND,
            List.of(new ScanCondition(Timeframe.D1, IndicatorType.ELDER_IMPULSE, null,
                    TrendState.ELDER_IMPULSE_GREEN, null, null)),
            null
        );

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }
}
