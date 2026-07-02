package walshe.projectcolumbo.api.v1;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import walshe.projectcolumbo.api.v1.dto.SignalListResponse;
import walshe.projectcolumbo.api.v1.dto.SignalSort;
import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.ingestion.IngestionStatusService;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.TrendState;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
class SignalController {

    private final SignalQueryService signalQueryService;
    private final IngestionStatusService ingestionStatusService;
    private final CandleFreshnessService freshnessService;

    SignalController(SignalQueryService signalQueryService,
                     IngestionStatusService ingestionStatusService,
                     CandleFreshnessService freshnessService) {
        this.signalQueryService = signalQueryService;
        this.ingestionStatusService = ingestionStatusService;
        this.freshnessService = freshnessService;
    }

    @GetMapping("/signals")
    ResponseEntity<?> getSignals(
            @Parameter(description = "Candle timeframe to query (D1 or W1)")
            @RequestParam Timeframe timeframe,
            @Parameter(description = "Indicator whose signal states to return")
            @RequestParam IndicatorType indicatorType,
            @Parameter(description = "Optional trend-state filter; when omitted, all states are returned")
            @RequestParam(required = false) TrendState state,
            @Parameter(description = "Optional result ordering; defaults to symbol ascending when omitted")
            @RequestParam(required = false) SignalSort sort,
            @Parameter(description = "When true, respond 503 instead of serving data that is stale beyond "
                    + "the ingestion grace window")
            @RequestParam(required = false, defaultValue = "false") boolean requireFresh) {

        if (requireFresh && freshnessService.isStaleBeyondGrace(timeframe)) {
            return StaleDataResponses.serviceUnavailable(timeframe, freshnessService);
        }

        List<SignalStateDto> signals = signalQueryService.listSignals(timeframe, indicatorType, state, sort);
        return ResponseEntity.ok(buildResponse(signals, timeframe));
    }

    @GetMapping("/assets/by-state")
    ResponseEntity<SignalListResponse> getAssetsByState(
            @Parameter(description = "Candle timeframe to query (D1 or W1)")
            @RequestParam Timeframe timeframe,
            @Parameter(description = "Indicator whose signal states to filter")
            @RequestParam IndicatorType indicatorType,
            @Parameter(description = "Trend state to filter assets by (required)")
            @RequestParam TrendState state) {

        List<SignalStateDto> signals = signalQueryService.listSignals(timeframe, indicatorType, state, null);
        return ResponseEntity.ok(buildResponse(signals, timeframe));
    }

    private SignalListResponse buildResponse(List<SignalStateDto> signals, Timeframe timeframe) {
        OffsetDateTime lastIngestionAt = ingestionStatusService.lastSuccessfulD1IngestionAt().orElse(null);
        LocalDate candlesThrough = ingestionStatusService.latestCandleDate().orElse(null);
        boolean stale = !freshnessService.isUpToDate(timeframe);
        return new SignalListResponse(signals, lastIngestionAt, candlesThrough, stale);
    }
}
