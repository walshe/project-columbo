package walshe.projectcolumbo.api.v1.summary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import walshe.projectcolumbo.api.v1.summary.dto.ConfluenceSummaryReport;

@RestController
@RequestMapping("/api/v1/summary/confluence")
@Tag(name = "Market Summary", description = "Endpoints for aggregate market summary reports")
public class ConfluenceSummaryController {

    private final ConfluenceSummaryService confluenceSummaryService;
    private final SummaryReportFormatter formatter;

    public ConfluenceSummaryController(ConfluenceSummaryService confluenceSummaryService,
                                       SummaryReportFormatter formatter) {
        this.confluenceSummaryService = confluenceSummaryService;
        this.formatter = formatter;
    }

    @GetMapping
    @Operation(
            summary = "Get cross-timeframe SuperTrend confluence",
            description = "Returns assets bullish on both W1 and D1 SuperTrend, and assets bearish on both, " +
                    "ordered by D1 flip date. Use format=MARKDOWN for a human-readable brief."
    )
    public ResponseEntity<?> getConfluence(
            @RequestParam(required = false, defaultValue = "JSON") SummaryFormat format) {

        ConfluenceSummaryReport report = confluenceSummaryService.getConfluence();

        if (format == SummaryFormat.MARKDOWN) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_MARKDOWN)
                    .body(formatter.formatConfluenceMarkdown(report));
        }

        return ResponseEntity.ok(report);
    }
}
