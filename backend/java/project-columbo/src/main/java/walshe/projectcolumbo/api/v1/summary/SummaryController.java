package walshe.projectcolumbo.api.v1.summary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import walshe.projectcolumbo.api.v1.summary.dto.SummaryReport;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.Timeframe;

@RestController
@RequestMapping("/api/v1/summary")
@Tag(name = "Market Summary", description = "Endpoints for aggregate market summary reports")
public class SummaryController {

    private final SummaryService summaryService;
    private final SummaryReportFormatter formatter;

    public SummaryController(SummaryService summaryService, SummaryReportFormatter formatter) {
        this.summaryService = summaryService;
        this.formatter = formatter;
    }

    @GetMapping
    @Operation(summary = "Get aggregate market summary", description = "Assembles market pulse, trending signals, and RSI scan results into a single report")
    public ResponseEntity<?> getSummary(
            @RequestParam Timeframe timeframe,
            @RequestParam(required = false) String format) {

        SummaryReport report = summaryService.getSummary(timeframe);

        if ("markdown".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_MARKDOWN)
                    .body(formatter.formatMarkdown(report));
        } else if ("html".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(formatter.formatHtml(report));
        }

        return ResponseEntity.ok(report);
    }
}
