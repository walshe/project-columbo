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
@RequestMapping("/api/v1/summary/trend-alignment")
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
            summary = "Get cross-timeframe SuperTrend trend alignment",
            description = "Returns assets bullish on both W1 and D1 SuperTrend, assets in bullish retest " +
                    "(W1 bullish, D1 recently bearish), and the equivalent bearish setups. " +
                    "Use format=MARKDOWN for a human-readable brief. " +
                    "Use maxRetestAgeDays to control the retest window (default 7)."
    )
    public ResponseEntity<?> getConfluence(
            @RequestParam(required = false, defaultValue = "JSON") SummaryFormat format,
            @RequestParam(required = false, defaultValue = "7") int maxRetestAgeDays) {

        ConfluenceSummaryReport report = confluenceSummaryService.getConfluence(maxRetestAgeDays);

        if (format == SummaryFormat.MARKDOWN) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_MARKDOWN)
                    .body(formatter.formatConfluenceMarkdown(report));
        }

        return ResponseEntity.ok(report);
    }
}
