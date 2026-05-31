package walshe.projectcolumbo.api.v1;

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
class ElderImpulseMarketPulseController {

    private final ElderImpulseMarketPulseQueryService queryService;

    ElderImpulseMarketPulseController(ElderImpulseMarketPulseQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/elder-impulse-market-pulse")
    ResponseEntity<MarketPulseDto> getLatestPulse(
            @RequestParam Timeframe timeframe) {

        return queryService.getLatestPulse(timeframe)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/elder-impulse-market-pulse/history")
    ResponseEntity<List<MarketPulseDto>> getPulseHistory(
            @RequestParam Timeframe timeframe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {

        List<MarketPulseDto> history = queryService.getPulseHistory(timeframe, from, to);
        return ResponseEntity.ok(history);
    }
}
