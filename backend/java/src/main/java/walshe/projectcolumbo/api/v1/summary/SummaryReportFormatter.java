package walshe.projectcolumbo.api.v1.summary;

import org.springframework.stereotype.Component;
import walshe.projectcolumbo.api.v1.dto.MarketPulseDto;
import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.api.v1.scan.dto.ElderImpulseMatch;
import walshe.projectcolumbo.api.v1.scan.dto.RsiMatch;
import walshe.projectcolumbo.api.v1.scan.dto.ScanResult;
import walshe.projectcolumbo.api.v1.scan.dto.SupertrendMatch;
import walshe.projectcolumbo.api.v1.scan.dto.ThermometerMatch;
import walshe.projectcolumbo.api.v1.summary.dto.ElderSummaryReport;
import walshe.projectcolumbo.api.v1.summary.dto.SummaryReport;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SummaryReportFormatter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm 'UTC'");

    // -------------------------------------------------------------------------
    // SuperTrend / RSI summary
    // -------------------------------------------------------------------------

    public String formatMarkdown(SummaryReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Market Summary Report\n\n");

        // Data freshness — always the first thing a reader should see.
        // candlesThrough tells them what date the signals are based on;
        // lastIngestionAt tells them when the pipeline that produced those signals ran.
        String candlesStr = report.candlesThrough() != null
                ? report.candlesThrough().format(DATE_FMT)
                : "unknown";
        String pipelineStr = report.lastIngestionAt() != null
                ? report.lastIngestionAt().atZoneSameInstant(java.time.ZoneOffset.UTC)
                        .format(DATETIME_FMT)
                : "never";
        sb.append(String.format("*Data through **%s** — pipeline ran %s*\n\n", candlesStr, pipelineStr));

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

    // -------------------------------------------------------------------------
    // Elder Impulse + Market Thermometer summary
    // -------------------------------------------------------------------------

    public String formatElderMarkdown(ElderSummaryReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Elder Impulse System — Daily Brief\n\n");

        // Data freshness — candlesThrough is the trading date the signals reflect;
        // lastIngestionAt is when the pipeline that derived them finished running.
        String candlesStr = report.candlesThrough() != null
                ? report.candlesThrough().format(DATE_FMT)
                : "unknown";
        String pipelineStr = report.lastIngestionAt() != null
                ? report.lastIngestionAt().atZoneSameInstant(java.time.ZoneOffset.UTC)
                        .format(DATETIME_FMT)
                : "never";
        sb.append(String.format("*Data through **%s** — pipeline ran %s*\n\n", candlesStr, pipelineStr));

        // --- Breadth ---
        sb.append("## Market Breadth\n\n");
        appendPulseLine(sb, "W1 Impulse (26-week EMA)", report.w1ImpulsePulse(), "GREEN", "RED", "NEUTRAL");
        appendPulseLine(sb, "D1 Impulse (13-EMA + MACD-H)", report.d1ImpulsePulse(), "GREEN", "RED", "NEUTRAL");
        appendPulseLine(sb, "D1 Thermometer (22-day EMA)", report.d1ThermometerPulse(), "QUIET", "HOT/SPIKE", "no data");
        sb.append("\n");

        // --- Primary shortlist ---
        sb.append("## Primary Shortlist — W1 GREEN + D1 GREEN + D1 QUIET\n");
        sb.append("*Assets passing all three Elder entry conditions tonight.*\n\n");
        if (report.primaryShortlist().isEmpty()) {
            sb.append("None tonight — conditions not aligned across the universe.\n\n");
        } else {
            for (ScanResult r : report.primaryShortlist()) {
                sb.append(String.format("- [%s](%s)", r.assetSymbol(), r.tradingviewUrl()));
                r.matchedIndicators().forEach(mi -> {
                    if (mi instanceof ElderImpulseMatch e) {
                        sb.append(String.format("  %s GREEN for %d day(s)", e.timeframe(), e.daysSinceChange()));
                    }
                    if (mi instanceof ThermometerMatch t) {
                        sb.append(String.format("  temp=%s ema=%s",
                                formatTemp(t.temperature()), formatTemp(t.temperatureEma())));
                        sb.append(String.format("  → target: yesterday high + %s", formatTemp(t.temperatureEma())));
                    }
                });
                sb.append(String.format("  (Vol: %s)\n", formatVolume(r.avgVolume7d())));
            }
            sb.append("\n");
        }

        // --- Fresh W1 flips ---
        sb.append("## Fresh W1 Green Flips (last 7 days)\n");
        sb.append("*Highest-conviction setups — the weekly engine just switched on.*\n\n");
        if (report.freshW1GreenFlips().isEmpty()) {
            sb.append("No fresh W1 flips this week.\n\n");
        } else {
            for (ScanResult r : report.freshW1GreenFlips()) {
                r.matchedIndicators().stream()
                        .filter(mi -> mi instanceof ElderImpulseMatch)
                        .map(mi -> (ElderImpulseMatch) mi)
                        .findFirst()
                        .ifPresent(e -> sb.append(String.format("- [%s](%s): W1 flipped GREEN %d day(s) ago (Vol: %s)\n",
                                r.assetSymbol(), r.tradingviewUrl(), e.daysSinceChange(),
                                formatVolume(r.avgVolume7d()))));
            }
            sb.append("\n");
        }

        // --- Spike alerts ---
        sb.append("## ⚠️ Spike Alerts — Take Profit\n");
        sb.append("*Temperature > 3× EMA. Crowd is overexcited. Close longs into strength, not new entries.*\n\n");
        if (report.spikeAlerts().isEmpty()) {
            sb.append("No active spike alerts.\n\n");
        } else {
            for (ScanResult r : report.spikeAlerts()) {
                r.matchedIndicators().stream()
                        .filter(mi -> mi instanceof ThermometerMatch)
                        .map(mi -> (ThermometerMatch) mi)
                        .findFirst()
                        .ifPresent(t -> sb.append(String.format(
                                "- [%s](%s): temp=%s, ema=%s (%.1f× normal) (Vol: %s)\n",
                                r.assetSymbol(), r.tradingviewUrl(),
                                formatTemp(t.temperature()),
                                formatTemp(t.temperatureEma()),
                                t.temperature().doubleValue() / t.temperatureEma().doubleValue(),
                                formatVolume(r.avgVolume7d()))));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private void appendPulseLine(StringBuilder sb, String label, MarketPulseDto pulse,
                                  String bullishLabel, String bearishLabel, String neutralLabel) {
        if (pulse == null) {
            sb.append(String.format("- **%s:** no data\n", label));
            return;
        }
        sb.append(String.format("- **%s:** %d %s / %d %s / %d %s  (%.0f%% %s)\n",
                label,
                pulse.bullishCount(), bullishLabel,
                pulse.bearishCount(), bearishLabel,
                pulse.missingCount(), neutralLabel,
                pulse.bullishRatio().doubleValue() * 100, bullishLabel));
    }

    private void appendSignals(StringBuilder sb, List<SignalStateDto> signals) {
        List<SignalStateDto> withFlip = signals.stream()
                .filter(s -> s.daysSinceFlip() != null)
                .collect(Collectors.toList());
        if (withFlip.isEmpty()) {
            sb.append("None found.\n\n");
        } else {
            for (SignalStateDto s : withFlip) {
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

    private String formatTemp(BigDecimal value) {
        if (value == null) return "0";
        return value.stripTrailingZeros().toPlainString();
    }

    private String formatVolume(BigDecimal volume) {
        if (volume == null || volume.compareTo(BigDecimal.ZERO) == 0) return "N/A";
        if (volume.compareTo(new BigDecimal("1000000")) >= 0) {
            return String.format("%.1fM", volume.doubleValue() / 1_000_000.0);
        }
        if (volume.compareTo(new BigDecimal("1000")) >= 0) {
            return String.format("%.1fK", volume.doubleValue() / 1_000.0);
        }
        return String.format("%.0f", volume.doubleValue());
    }
}
