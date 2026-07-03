package walshe.projectcolumbo.api.v1.summary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import walshe.projectcolumbo.api.v1.CandleFreshnessService;
import walshe.projectcolumbo.api.v1.StaleDataResponses;
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
            @Parameter(description = "Candle timeframe the summary is built for (D1 or W1)")
            @RequestParam Timeframe timeframe,
            @Parameter(description = "Output format: JSON (default), MARKDOWN (human-readable brief), or "
                    + "WATCHLIST (TradingView-importable text — '###Section' headers + EXCHANGE:SYMBOL lines "
                    + "for pulling every flagged asset into one chart)")
            @RequestParam(required = false, defaultValue = "JSON") SummaryFormat format,
            @Parameter(description = "When true, respond 503 instead of serving data that is stale beyond "
                    + "the ingestion grace window")
            @RequestParam(required = false, defaultValue = "false") boolean requireFresh) {

        if (requireFresh && freshnessService.isStaleBeyondGrace(timeframe)) {
            return StaleDataResponses.serviceUnavailable(timeframe, freshnessService);
        }

        SummaryReport report = summaryService.getSummary(timeframe);

        if (format == SummaryFormat.MARKDOWN) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_MARKDOWN)
                    .body(formatter.formatMarkdown(report));
        }

        if (format == SummaryFormat.WATCHLIST) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(formatter.formatWatchlist(report));
        }

        return ResponseEntity.ok(report);
    }
}
