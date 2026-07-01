package walshe.projectcolumbo.api.v1.summary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import walshe.projectcolumbo.api.v1.CandleFreshnessService;
import walshe.projectcolumbo.api.v1.dto.StaleDataError;
import walshe.projectcolumbo.api.v1.summary.dto.SummaryReport;
import walshe.projectcolumbo.persistence.model.Timeframe;

@RestController
@RequestMapping("/api/v1/summary")
@Tag(name = "Market Summary", description = "Endpoints for aggregate market summary reports")
public class SummaryController {

    private final SummaryService summaryService;
    private final SummaryReportFormatter formatter;
    private final CandleFreshnessService freshnessService;

    public SummaryController(SummaryService summaryService, SummaryReportFormatter formatter,
                             CandleFreshnessService freshnessService) {
        this.summaryService = summaryService;
        this.formatter = formatter;
        this.freshnessService = freshnessService;
    }

    @GetMapping
    @Operation(
            summary = "Get aggregate market summary",
            description = "Assembles SuperTrend market pulse, recent signal flips, and RSI scan results. " +
                    "Use format=MARKDOWN for a human-readable brief."
    )
    public ResponseEntity<?> getSummary(
            @RequestParam Timeframe timeframe,
            @RequestParam(required = false, defaultValue = "JSON") SummaryFormat format,
            @RequestParam(required = false, defaultValue = "false") boolean requireFresh) {

        if (requireFresh && freshnessService.isStaleBeyondGrace(timeframe)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("Retry-After", "300")
                    .body(new StaleDataError(
                            "Candle data for " + timeframe + " is stale (missing the most recent finalized candle).",
                            timeframe.name(),
                            freshnessService.expectedLatest(timeframe)));
        }

        SummaryReport report = summaryService.getSummary(timeframe);

        if (format == SummaryFormat.MARKDOWN) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_MARKDOWN)
                    .body(formatter.formatMarkdown(report));
        }

        return ResponseEntity.ok(report);
    }
}
