package walshe.projectcolumbo.api.v1;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import walshe.projectcolumbo.api.v1.dto.MarketPulseDto;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.Timeframe;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
class MarketPulseController {

    private final MarketPulseQueryService marketPulseQueryService;

    MarketPulseController(MarketPulseQueryService marketPulseQueryService) {
        this.marketPulseQueryService = marketPulseQueryService;
    }

    @GetMapping("/market-pulse")
    ResponseEntity<MarketPulseDto> getLatestPulse(
            @RequestParam Timeframe timeframe,
            @RequestParam IndicatorType indicatorType) {
        
        return marketPulseQueryService.getLatestPulse(timeframe, indicatorType)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/market-pulse/history")
    ResponseEntity<List<MarketPulseDto>> getPulseHistory(
            @RequestParam Timeframe timeframe,
            @RequestParam IndicatorType indicatorType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        
        List<MarketPulseDto> history = marketPulseQueryService.getPulseHistory(timeframe, indicatorType, from, to);
        return ResponseEntity.ok(history);
    }
}
