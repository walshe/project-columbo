package walshe.projectcolumbo.api.v1;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import walshe.projectcolumbo.api.v1.dto.StaleDataError;
import walshe.projectcolumbo.persistence.model.Timeframe;

/**
 * Shared builder for the 503 returned by read endpoints when {@code requireFresh=true} and the
 * data is stale beyond the grace window. Centralised so the status, body, and {@code Retry-After}
 * hint are defined once across the signal and summary controllers.
 */
public final class StaleDataResponses {

    private StaleDataResponses() {}

    public static ResponseEntity<StaleDataError> serviceUnavailable(Timeframe timeframe,
                                                                    CandleFreshnessService freshnessService) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", String.valueOf(freshnessService.secondsUntilNextBoundary()))
                .body(new StaleDataError(
                        "Candle data for " + timeframe + " is stale (missing the most recent finalized candle).",
                        timeframe.name(),
                        freshnessService.expectedLatest(timeframe)));
    }
}
