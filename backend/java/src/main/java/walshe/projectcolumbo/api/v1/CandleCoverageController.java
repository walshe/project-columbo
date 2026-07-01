package walshe.projectcolumbo.api.v1;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import walshe.projectcolumbo.api.v1.dto.CandleCoverageDto;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
class CandleCoverageController {

    private final CandleCoverageService candleCoverageService;

    CandleCoverageController(CandleCoverageService candleCoverageService) {
        this.candleCoverageService = candleCoverageService;
    }

    @GetMapping("/candles/coverage")
    ResponseEntity<Map<String, CandleCoverageDto>> getCoverage() {
        return ResponseEntity.ok(candleCoverageService.getCoverage());
    }
}
