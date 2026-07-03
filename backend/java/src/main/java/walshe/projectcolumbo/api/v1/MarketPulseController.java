package walshe.projectcolumbo.api.v1;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import walshe.projectcolumbo.api.v1.dto.MarketPulseDto;
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

    @GetMapping("/supertrend-market-pulse")
    ResponseEntity<MarketPulseDto> getLatestPulse(
            @Parameter(description = "Candle timeframe of the breadth snapshot (D1 or W1)")
            @RequestParam Timeframe timeframe) {

        return marketPulseQueryService.getLatestPulse(timeframe)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/supertrend-market-pulse/history")
    ResponseEntity<List<MarketPulseDto>> getPulseHistory(
            @Parameter(description = "Candle timeframe of the breadth snapshots (D1 or W1)")
            @RequestParam Timeframe timeframe,
            @Parameter(description = "Optional inclusive lower bound (ISO-8601) on snapshot close time")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "Optional inclusive upper bound (ISO-8601) on snapshot close time")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {

        List<MarketPulseDto> history = marketPulseQueryService.getPulseHistory(timeframe, from, to);
        return ResponseEntity.ok(history);
    }
}
