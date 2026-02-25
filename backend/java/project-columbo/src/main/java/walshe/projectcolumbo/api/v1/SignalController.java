package walshe.projectcolumbo.api.v1;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import walshe.projectcolumbo.api.v1.dto.SignalSort;
import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.persistence.IndicatorType;
import walshe.projectcolumbo.persistence.Timeframe;
import walshe.projectcolumbo.persistence.TrendState;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
class SignalController {

    private final SignalQueryService signalQueryService;

    SignalController(SignalQueryService signalQueryService) {
        this.signalQueryService = signalQueryService;
    }

    @GetMapping("/signals")
    ResponseEntity<List<SignalStateDto>> getSignals(
            @RequestParam Timeframe timeframe,
            @RequestParam IndicatorType indicatorType,
            @RequestParam(required = false) TrendState state,
            @RequestParam(required = false) SignalSort sort) {
        
        List<SignalStateDto> signals = signalQueryService.listSignals(timeframe, indicatorType, state, sort);
        return ResponseEntity.ok(signals);
    }

    @GetMapping("/assets/by-state")
    ResponseEntity<List<String>> getAssetsByState(
            @RequestParam Timeframe timeframe,
            @RequestParam IndicatorType indicatorType,
            @RequestParam TrendState state) {
        
        List<String> symbols = signalQueryService.listSignals(timeframe, indicatorType, state, null)
                .stream()
                .map(SignalStateDto::symbol)
                .collect(Collectors.toList());
        return ResponseEntity.ok(symbols);
    }
}
