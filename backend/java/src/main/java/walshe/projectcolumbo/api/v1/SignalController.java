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

    SignalController(SignalQueryService signalQueryService,
                     IngestionStatusService ingestionStatusService) {
        this.signalQueryService = signalQueryService;
        this.ingestionStatusService = ingestionStatusService;
    }

    @GetMapping("/signals")
    ResponseEntity<SignalListResponse> getSignals(
            @RequestParam Timeframe timeframe,
            @RequestParam IndicatorType indicatorType,
            @RequestParam(required = false) TrendState state,
            @RequestParam(required = false) SignalSort sort) {

        List<SignalStateDto> signals = signalQueryService.listSignals(timeframe, indicatorType, state, sort);
        OffsetDateTime lastIngestionAt = ingestionStatusService.lastSuccessfulD1IngestionAt().orElse(null);
        LocalDate candlesThrough = ingestionStatusService.latestCandleDate().orElse(null);
        return ResponseEntity.ok(new SignalListResponse(signals, lastIngestionAt, candlesThrough));
    }

    @GetMapping("/assets/by-state")
    ResponseEntity<SignalListResponse> getAssetsByState(
            @RequestParam Timeframe timeframe,
            @RequestParam IndicatorType indicatorType,
            @RequestParam TrendState state) {

        List<SignalStateDto> signals = signalQueryService.listSignals(timeframe, indicatorType, state, null);
        OffsetDateTime lastIngestionAt = ingestionStatusService.lastSuccessfulD1IngestionAt().orElse(null);
        LocalDate candlesThrough = ingestionStatusService.latestCandleDate().orElse(null);
        return ResponseEntity.ok(new SignalListResponse(signals, lastIngestionAt, candlesThrough));
    }
}
