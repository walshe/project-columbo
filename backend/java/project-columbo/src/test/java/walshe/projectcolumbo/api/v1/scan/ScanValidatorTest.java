package walshe.projectcolumbo.api.v1.scan;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.api.exception.BadRequestException;
import walshe.projectcolumbo.api.v1.scan.dto.ScanCondition;
import walshe.projectcolumbo.api.v1.scan.dto.ScanOperator;
import walshe.projectcolumbo.api.v1.scan.dto.ScanRequest;
import walshe.projectcolumbo.persistence.IndicatorType;
import walshe.projectcolumbo.persistence.SignalEvent;
import walshe.projectcolumbo.persistence.Timeframe;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScanValidatorTest {

    private final ScanValidator validator = new ScanValidator();

    @Test
    void shouldAcceptValidSuperTrendCondition() {
        ScanRequest request = new ScanRequest(
            Timeframe.D1,
            ScanOperator.AND,
            List.of(new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.BULLISH_REVERSAL))
        );

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    void shouldAcceptValidRsiCondition() {
        ScanRequest request = new ScanRequest(
            Timeframe.D1,
            ScanOperator.AND,
            List.of(new ScanCondition(IndicatorType.RSI, SignalEvent.CROSSED_ABOVE_60))
        );

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    void shouldRejectEmptyConditions() {
        ScanRequest request = new ScanRequest(
            Timeframe.D1,
            ScanOperator.AND,
            Collections.emptyList()
        );

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }

    @Test
    void shouldRejectInvalidEventForSuperTrend() {
        ScanRequest request = new ScanRequest(
            Timeframe.D1,
            ScanOperator.AND,
            List.of(new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.CROSSED_ABOVE_60))
        );

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }

    @Test
    void shouldRejectInvalidEventForRsi() {
        ScanRequest request = new ScanRequest(
            Timeframe.D1,
            ScanOperator.AND,
            List.of(new ScanCondition(IndicatorType.RSI, SignalEvent.BEARISH_REVERSAL))
        );

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }

    @Test
    void shouldRejectNoneEvent() {
        ScanRequest request = new ScanRequest(
            Timeframe.D1,
            ScanOperator.AND,
            List.of(new ScanCondition(IndicatorType.SUPERTREND, SignalEvent.NONE))
        );

        assertThrows(BadRequestException.class, () -> validator.validate(request));
    }
}
