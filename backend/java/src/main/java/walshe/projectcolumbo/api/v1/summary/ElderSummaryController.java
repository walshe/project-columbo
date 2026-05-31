package walshe.projectcolumbo.api.v1.summary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import walshe.projectcolumbo.api.v1.summary.dto.ElderSummaryReport;

@RestController
@RequestMapping("/api/v1/elder-summary")
@Tag(name = "Market Summary", description = "Endpoints for aggregate market summary reports")
public class ElderSummaryController {

    private final ElderSummaryService elderSummaryService;
    private final SummaryReportFormatter formatter;

    public ElderSummaryController(ElderSummaryService elderSummaryService,
                                   SummaryReportFormatter formatter) {
        this.elderSummaryService = elderSummaryService;
        this.formatter = formatter;
    }

    @GetMapping
    @Operation(
            summary = "Get Elder Impulse System daily brief",
            description = """
                    Cross-timeframe Elder Impulse + Market Thermometer report. No timeframe
                    parameter — Elder always uses both W1 (weekly trend direction) and D1
                    (daily permission state) together.

                    Returns:
                    - W1 and D1 Impulse breadth (GREEN/RED/NEUTRAL counts across all assets)
                    - D1 Thermometer breadth (QUIET/HOT/SPIKE counts)
                    - Primary shortlist: assets passing W1 GREEN + D1 GREEN + D1 QUIET tonight
                    - Fresh W1 flips to GREEN this week (highest-conviction setups)
                    - Active spike alerts (temperature > 3× EMA — take profits, not new entries)

                    Use format=MARKDOWN for a human-readable evening brief.
                    """
    )
    public ResponseEntity<?> getElderSummary(
            @RequestParam(required = false, defaultValue = "JSON") SummaryFormat format) {

        ElderSummaryReport report = elderSummaryService.getSummary();

        if (format == SummaryFormat.MARKDOWN) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_MARKDOWN)
                    .body(formatter.formatElderMarkdown(report));
        }

        return ResponseEntity.ok(report);
    }
}
