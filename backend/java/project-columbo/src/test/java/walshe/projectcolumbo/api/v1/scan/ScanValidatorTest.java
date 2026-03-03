package walshe.projectcolumbo.api.v1.scan;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.api.exception.BadRequestException;
import walshe.projectcolumbo.api.v1.scan.dto.ScanCondition;
import walshe.projectcolumbo.api.v1.scan.dto.ScanOperator;
import walshe.projectcolumbo.api.v1.scan.dto.ScanRequest;
import walshe.projectcolumbo.persistence.IndicatorType;
import walshe.projectcolumbo.persistence.SignalEvent;
import walshe.projectcolumbo.persistence.TrendState;
import walshe.projectcolumbo.persistence.Timeframe;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScanValidatorTest {

    private final ScanValidator validator = new ScanValidator();

    @Test
    void shouldAcceptValidSuperTrendEventCondition() {
        ScanRequest request = new ScanRequest(
            Timeframe.D1,
            ScanOperator.AND,
            List.of(new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, null, null)),
            null
        );

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    void shouldAcceptValidSuperTrendStateCondition() {
        ScanRequest request = new ScanRequest(
            Timeframe.D1,
            ScanOperator.AND,
            List.of(new ScanCondition(IndicatorType.SUPERTREND, null, TrendState.BULLISH, 5)),
            null
        );

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    void shouldAcceptValidRsiEventCondition() {
        ScanRequest request = new ScanRequest(
            Timeframe.D1,
            ScanOperator.AND,
            List.of(new ScanCondition(IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60, null, null)),
            null
        );

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    void shouldAcceptValidRsiStateCondition() {
        ScanRequest request = new ScanRequest(
            Timeframe.D1,
            ScanOperator.AND,
            List.of(new ScanCondition(IndicatorType.RSI, null, TrendState.ABOVE_60, null)),
            null
        );

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    void shouldRejectEmptyConditions() {
        ScanRequest request = new ScanRequest(
            Timeframe.D1,
            ScanOperator.AND,
            Collections.emptyList(),
            null
        );

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }

    @Test
    void shouldRejectMissingEventAndState() {
        ScanRequest request = new ScanRequest(
            Timeframe.D1,
            ScanOperator.AND,
            List.of(new ScanCondition(IndicatorType.SUPERTREND, null, null, null)),
            null
        );

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }

    @Test
    void shouldRejectInvalidEventForSuperTrend() {
        ScanRequest request = new ScanRequest(
            Timeframe.D1,
            ScanOperator.AND,
            List.of(new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.CROSSED_ABOVE_60, null, null)),
            null
        );

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }

    @Test
    void shouldRejectInvalidStateForSuperTrend() {
        ScanRequest request = new ScanRequest(
            Timeframe.D1,
            ScanOperator.AND,
            List.of(new ScanCondition(IndicatorType.SUPERTREND, null, TrendState.ABOVE_60, null)),
            null
        );

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }

    @Test
    void shouldRejectMaxDaysSinceFlipWithoutState() {
        ScanRequest request = new ScanRequest(
            Timeframe.D1,
            ScanOperator.AND,
            List.of(new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL, null, 5)),
            null
        );

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }

    @Test
    void shouldRejectNoneEvent() {
        ScanRequest request = new ScanRequest(
            Timeframe.D1,
            ScanOperator.AND,
            List.of(new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.NONE, null, null)),
            null
        );

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }
}
