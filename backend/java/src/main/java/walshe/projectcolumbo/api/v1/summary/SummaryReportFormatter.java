package walshe.projectcolumbo.api.v1.summary;

import org.springframework.stereotype.Component;
import walshe.projectcolumbo.api.v1.dto.MarketPulseDto;
import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.api.v1.scan.dto.RsiMatch;
import walshe.projectcolumbo.api.v1.scan.dto.ScanResult;
import walshe.projectcolumbo.api.v1.scan.dto.SupertrendMatch;
import walshe.projectcolumbo.api.v1.summary.dto.SummaryReport;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class SummaryReportFormatter {

    public String formatMarkdown(SummaryReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Market Summary Report\n\n");

        if (report.pulse() != null) {
            MarketPulseDto pulse = report.pulse();
            sb.append("## Market Pulse\n");
            sb.append(String.format("- **Bullish:** %d\n", pulse.bullishCount()));
            sb.append(String.format("- **Bearish:** %d\n", pulse.bearishCount()));
            sb.append(String.format("- **Bullish Ratio:** %.2f%%\n\n", pulse.bullishRatio().doubleValue() * 100));
        }

        sb.append("## Recent Bullish Flips\n");
        appendSignals(sb, report.bullishSignals());

        sb.append("## Recent Bearish Flips\n");
        appendSignals(sb, report.bearishSignals());

        sb.append("## Bullish Trend + RSI Cross Above 60\n");
        appendScanResults(sb, report.bullishRsiOverbought());

        sb.append("## Bearish Trend + RSI Cross Below 40\n");
        appendScanResults(sb, report.bearishRsiOversold());

        return sb.toString();
    }

    public String formatHtml(SummaryReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h1>Market Summary Report</h1>");

        if (report.pulse() != null) {
            MarketPulseDto pulse = report.pulse();
            sb.append("<h2>Market Pulse</h2><ul>");
            sb.append(String.format("<li><strong>Bullish:</strong> %d</li>", pulse.bullishCount()));
            sb.append(String.format("<li><strong>Bearish:</strong> %d</li>", pulse.bearishCount()));
            sb.append(String.format("<li><strong>Bullish Ratio:</strong> %.2f%%</li>", pulse.bullishRatio().doubleValue() * 100));
            sb.append("</ul>");
        }

        sb.append("<h2>Recent Bullish Flips</h2>");
        appendSignalsHtml(sb, report.bullishSignals());

        sb.append("<h2>Recent Bearish Flips</h2>");
        appendSignalsHtml(sb, report.bearishSignals());

        sb.append("<h2>Bullish Trend + RSI Cross Above 60</h2>");
        appendScanResultsHtml(sb, report.bullishRsiOverbought());

        sb.append("<h2>Bearish Trend + RSI Cross Below 40</h2>");
        appendScanResultsHtml(sb, report.bearishRsiOversold());

        return sb.toString();
    }

    private void appendSignals(StringBuilder sb, List<SignalStateDto> signals) {
        if (signals.isEmpty()) {
            sb.append("None found.\n\n");
        } else {
            for (SignalStateDto s : signals) {
                sb.append(String.format("- [%s](%s): Flipped %d days ago (Vol: %s)\n", 
                        s.symbol(), s.tradingviewUrl(), s.daysSinceFlip(), formatVolume(s.avgVolume7d())));
            }
            sb.append("\n");
        }
    }

    private void appendScanResults(StringBuilder sb, List<ScanResult> results) {
        if (results.isEmpty()) {
            sb.append("None found.\n\n");
        } else {
            for (ScanResult r : results) {
                String details = r.matchedIndicators().stream()
                        .map(mi -> {
                            if (mi instanceof SupertrendMatch sm) return String.format("Supertrend flipped %d days ago", sm.daysSinceFlip());
                            if (mi instanceof RsiMatch rm) return String.format("RSI crossed %d days ago (Value: %.1f)", rm.daysSinceCross(), rm.rsiValue());
                            return mi.indicatorType().toString();
                        })
                        .collect(Collectors.joining(", "));
                sb.append(String.format("- [%s](%s): %s (Vol: %s)\n", 
                        r.assetSymbol(), r.tradingviewUrl(), details, formatVolume(r.avgVolume7d())));
            }
            sb.append("\n");
        }
    }

    private void appendSignalsHtml(StringBuilder sb, List<SignalStateDto> signals) {
        if (signals.isEmpty()) {
            sb.append("<p>None found.</p>");
        } else {
            sb.append("<ul>");
            for (SignalStateDto s : signals) {
                sb.append(String.format("<li><a href=\"%s\">%s</a>: Flipped %d days ago (Vol: %s)</li>", 
                        s.tradingviewUrl(), s.symbol(), s.daysSinceFlip(), formatVolume(s.avgVolume7d())));
            }
            sb.append("</ul>");
        }
    }

    private void appendScanResultsHtml(StringBuilder sb, List<ScanResult> results) {
        if (results.isEmpty()) {
            sb.append("<p>None found.</p>");
        } else {
            sb.append("<ul>");
            for (ScanResult r : results) {
                String details = r.matchedIndicators().stream()
                        .map(mi -> {
                            if (mi instanceof SupertrendMatch sm) return String.format("Supertrend flipped %d days ago", sm.daysSinceFlip());
                            if (mi instanceof RsiMatch rm) return String.format("RSI crossed %d days ago (Value: %.1f)", rm.daysSinceCross(), rm.rsiValue());
                            return mi.indicatorType().toString();
                        })
                        .collect(Collectors.joining(", "));
                sb.append(String.format("<li><a href=\"%s\">%s</a>: %s (Vol: %s)</li>", 
                        r.tradingviewUrl(), r.assetSymbol(), details, formatVolume(r.avgVolume7d())));
            }
            sb.append("</ul>");
        }
    }

    private String formatVolume(java.math.BigDecimal volume) {
        if (volume == null || volume.compareTo(java.math.BigDecimal.ZERO) == 0) return "N/A";
        if (volume.compareTo(new java.math.BigDecimal("1000000")) >= 0) {
            return String.format("%.1fM", volume.doubleValue() / 1000000.0);
        }
        if (volume.compareTo(new java.math.BigDecimal("1000")) >= 0) {
            return String.format("%.1fK", volume.doubleValue() / 1000.0);
        }
        return String.format("%.0f", volume.doubleValue());
    }
}
