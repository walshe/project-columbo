package walshe.projectcolumbo.api.v1;

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
            @RequestParam Timeframe timeframe,
            @RequestParam IndicatorType indicatorType,
            @RequestParam(required = false) TrendState state,
            @RequestParam(required = false) SignalSort sort,
            @RequestParam(required = false, defaultValue = "false") boolean requireFresh) {

        if (requireFresh && freshnessService.isStaleBeyondGrace(timeframe)) {
            return StaleDataResponses.serviceUnavailable(timeframe, freshnessService);
        }

        List<SignalStateDto> signals = signalQueryService.listSignals(timeframe, indicatorType, state, sort);
        return ResponseEntity.ok(buildResponse(signals, timeframe));
    }

    @GetMapping("/assets/by-state")
    ResponseEntity<SignalListResponse> getAssetsByState(
            @RequestParam Timeframe timeframe,
            @RequestParam IndicatorType indicatorType,
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
