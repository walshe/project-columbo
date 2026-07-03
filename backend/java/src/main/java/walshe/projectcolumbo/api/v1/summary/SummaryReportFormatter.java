package walshe.projectcolumbo.api.v1.summary;

import org.springframework.stereotype.Component;
import walshe.projectcolumbo.api.v1.dto.MarketPulseDto;
import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.api.v1.scan.dto.ElderImpulseMatch;
import walshe.projectcolumbo.api.v1.scan.dto.RsiMatch;
import walshe.projectcolumbo.api.v1.scan.dto.ScanResult;
import walshe.projectcolumbo.api.v1.scan.dto.SupertrendMatch;
import walshe.projectcolumbo.api.v1.scan.dto.ThermometerMatch;
import walshe.projectcolumbo.api.v1.summary.dto.ConfluenceSummaryReport;
import walshe.projectcolumbo.api.v1.summary.dto.ElderSummaryReport;
import walshe.projectcolumbo.api.v1.summary.dto.SummaryReport;
import walshe.projectcolumbo.api.v1.util.TradingViewUtil;

import walshe.projectcolumbo.persistence.model.Timeframe;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    // Cross-timeframe SuperTrend confluence summary
    // -------------------------------------------------------------------------

    public String formatConfluenceMarkdown(ConfluenceSummaryReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# SuperTrend Confluence Report\n\n");

        String candlesStr = report.candlesThrough() != null
                ? report.candlesThrough().format(DATE_FMT)
                : "unknown";
        String pipelineStr = report.lastIngestionAt() != null
                ? report.lastIngestionAt().atZoneSameInstant(java.time.ZoneOffset.UTC).format(DATETIME_FMT)
                : "never";
        sb.append(String.format("*Data through **%s** — pipeline ran %s*\n\n", candlesStr, pipelineStr));

        sb.append("## W1 + D1 Bullish Confluence\n");
        sb.append("*Assets bullish on both weekly and daily SuperTrend — ordered by when D1 aligned (most recent first)*\n\n");
        appendConfluenceSignals(sb, report.bullishConfluence());

        sb.append("## W1 + D1 Bullish Retest\n");
        sb.append("*W1 bullish but D1 recently pulled bearish — potential continuation setup*\n\n");
        appendRetestSignals(sb, report.bullishRetest());

        sb.append("## W1 + D1 Bearish Confluence\n");
        sb.append("*Assets bearish on both weekly and daily SuperTrend — ordered by when D1 aligned (most recent first)*\n\n");
        appendConfluenceSignals(sb, report.bearishConfluence());

        sb.append("## W1 + D1 Bearish Retest\n");
        sb.append("*W1 bearish but D1 recently pulled bullish — potential continuation setup*\n\n");
        appendRetestSignals(sb, report.bearishRetest());

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // TradingView watchlist export (###Section headers + EXCHANGE:SYMBOL lines)
    // -------------------------------------------------------------------------

    public String formatWatchlist(SummaryReport report) {
        StringBuilder sb = new StringBuilder();
        // Mirror the Markdown "Recent … Flips" sections, which show only assets with a recorded flip.
        appendWatchlistSection(sb, "Recent Bullish Flips", flipSymbols(report.bullishSignals()));
        appendWatchlistSection(sb, "Recent Bearish Flips", flipSymbols(report.bearishSignals()));
        appendWatchlistSection(sb, "Bullish Trend + RSI Cross Above 60", scanSymbols(report.bullishRsiOverbought()));
        appendWatchlistSection(sb, "Bearish Trend + RSI Cross Below 40", scanSymbols(report.bearishRsiOversold()));
        return sb.toString();
    }

    public String formatConfluenceWatchlist(ConfluenceSummaryReport report) {
        StringBuilder sb = new StringBuilder();
        appendWatchlistSection(sb, "W1 + D1 Bullish Confluence", signalSymbols(report.bullishConfluence()));
        appendWatchlistSection(sb, "W1 + D1 Bullish Retest", signalSymbols(report.bullishRetest()));
        appendWatchlistSection(sb, "W1 + D1 Bearish Confluence", signalSymbols(report.bearishConfluence()));
        appendWatchlistSection(sb, "W1 + D1 Bearish Retest", signalSymbols(report.bearishRetest()));
        return sb.toString();
    }

    private void appendWatchlistSection(StringBuilder sb, String header, List<String> symbols) {
        if (symbols.isEmpty()) {
            return; // omit empty sections rather than emit a stray ###header
        }
        sb.append("###").append(header).append("\n");
        for (String symbol : symbols) {
            sb.append(symbol).append("\n");
        }
    }

    private List<String> signalSymbols(List<SignalStateDto> signals) {
        return signals.stream()
                .map(s -> TradingViewUtil.watchlistSymbol(s.tradingviewUrl()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<String> flipSymbols(List<SignalStateDto> signals) {
        return signals.stream()
                .filter(s -> s.daysSinceFlip() != null)
                .map(s -> TradingViewUtil.watchlistSymbol(s.tradingviewUrl()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<String> scanSymbols(List<ScanResult> results) {
        return results.stream()
                .map(r -> TradingViewUtil.watchlistSymbol(r.tradingviewUrl()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void appendConfluenceSignals(StringBuilder sb, List<SignalStateDto> signals) {
        if (signals.isEmpty()) {
            sb.append("None — no cross-timeframe alignment found.\n\n");
            return;
        }
        for (SignalStateDto s : signals) {
            String flipStr = s.daysSinceFlip() != null
                    ? s.daysSinceFlip() + " day(s) ago"
                    : "established";
            sb.append(String.format("- [%s](%s): D1 aligned %s (%s)\n",
                    s.symbol(), s.tradingviewUrl(), flipStr, formatVolAndPct(s.avgVolume7d(), s.pctChangeSinceFlip())));
        }
        sb.append("\n");
    }

    private void appendRetestSignals(StringBuilder sb, List<SignalStateDto> signals) {
        if (signals.isEmpty()) {
            sb.append("None — no retest setups found.\n\n");
            return;
        }
        for (SignalStateDto s : signals) {
            String flipStr = s.daysSinceFlip() != null
                    ? s.daysSinceFlip() + " day(s) ago"
                    : "recently";
            sb.append(String.format("- [%s](%s): D1 counter-trend %s (%s)\n",
                    s.symbol(), s.tradingviewUrl(), flipStr, formatVolAndPct(s.avgVolume7d(), s.pctChangeSinceFlip())));
        }
        sb.append("\n");
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
        sb.append("*W1 sets the strategic direction — trade only with the weekly trend on the daily chart. " +
                "D1 shows how many assets have daily trade permission today (GREEN = enter long, RED = enter short, NEUTRAL = stand aside). " +
                "Thermometer shows whether entry conditions are calm.*\n\n");
        appendPulseLine(sb, "W1 Impulse (26-week EMA)", report.w1ImpulsePulse(), "GREEN", "RED", "NEUTRAL");
        appendPulseLine(sb, "D1 Impulse (13-EMA + MACD-H)", report.d1ImpulsePulse(), "GREEN", "RED", "NEUTRAL");
        appendPulseLine(sb, "D1 Thermometer (22-day EMA)", report.d1ThermometerPulse(), "QUIET", "HOT/SPIKE", "no data",
                "*(QUIET = calm, enter here · HOT = slippage risk, caution · SPIKE = take profits, not new entries)*");
        int totalAssets = report.w1ImpulsePulse() != null ? report.w1ImpulsePulse().totalAssets() : 0;
        sb.append(String.format("- **Cross-timeframe alignment:** %d bull-aligned (W1+D1 GREEN) / %d bear-aligned (W1+D1 RED)%s\n",
                report.w1d1BullAlignedCount(),
                report.w1d1BearAlignedCount(),
                totalAssets > 0 ? String.format("  *(%d%% of universe trending with conviction in either direction)*",
                        (report.w1d1BullAlignedCount() + report.w1d1BearAlignedCount()) * 100 / totalAssets) : ""));
        sb.append("\n");
        appendBreadthConclusion(sb, report.w1ImpulsePulse(), report.d1ThermometerPulse());
        sb.append("\n");

        // Determine macro bias for conditional section ordering
        boolean bearBias = report.w1ImpulsePulse() != null &&
                (1.0 - report.w1ImpulsePulse().bullishRatio().doubleValue()) > 0.5;

        if (bearBias) {
            appendBearShortlist(sb, report);
            appendBullShortlist(sb, report);
        } else {
            appendBullShortlist(sb, report);
            appendBearShortlist(sb, report);
        }
        appendFreshSignalObservation(sb, report.primaryShortlist(), report.primaryBearShortlist());

        // --- Fresh W1 green flips ---
        sb.append("## Fresh W1 Green Flips (last 7 days)\n");
        sb.append("*Weekly 26-EMA just turned up — strategic bull direction established. No D1 permission yet. Watch each evening for D1 GREEN + QUIET to graduate to the bull shortlist.*\n\n");
        appendW1Flips(sb, report.freshW1GreenFlips(), "GREEN");

        // --- Fresh W1 red flips ---
        sb.append("## Fresh W1 Red Flips (last 7 days)\n");
        sb.append("*Weekly 26-EMA just turned down — strategic bear direction established. Tighten stops aggressively on any longs in these names. Watch for D1 RED + QUIET alignment to enter short.*\n\n");
        appendW1Flips(sb, report.freshW1RedFlips(), "RED");

        // --- Spike alerts ---
        sb.append("## ⚠️ Spike Alerts — Take Profit\n");
        sb.append("*Temperature > 3× EMA. Panics and surges tend to be short-lived — Elder calls these gifts from the crowd. Do not open new positions.*\n\n");
        boolean anySpikeAlerts = !report.spikeAlertsW1Green().isEmpty()
                || !report.spikeAlertsW1Red().isEmpty()
                || !report.spikeAlertsW1Neutral().isEmpty();
        if (!anySpikeAlerts) {
            sb.append("No active spike alerts.\n\n");
        } else {
            if (!report.spikeAlertsW1Green().isEmpty()) {
                sb.append("*W1 GREEN — spike in an uptrend. Take profits on longs, do not add.*\n");
                appendSpikeList(sb, report.spikeAlertsW1Green());
            }
            if (!report.spikeAlertsW1Red().isEmpty()) {
                sb.append("*W1 RED — short-covering rally into a downtrend. Consider selling into this strength.*\n");
                appendSpikeList(sb, report.spikeAlertsW1Red());
            }
            if (!report.spikeAlertsW1Neutral().isEmpty()) {
                sb.append("*W1 NEUTRAL — no weekly direction established. Context ambiguous; caution on directional bias.*\n");
                appendSpikeList(sb, report.spikeAlertsW1Neutral());
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private static final int FRESH_SIGNAL_DAYS = 3;

    private void appendBullShortlist(StringBuilder sb, ElderSummaryReport report) {
        sb.append("## Primary Bull Shortlist — W1 GREEN + D1 GREEN + D1 QUIET\n");
        sb.append("*Weekly EMA rising (strategic bull) + daily EMA and MACD-H both rising (inertia and momentum in gear) + market quiet (low slippage entry). Get long. Hop off the moment a single daily indicator turns.*\n\n");
        if (report.primaryShortlist().isEmpty()) {
            sb.append("None tonight — inertia and momentum not in gear across the universe.\n\n");
        } else {
            for (ScanResult r : report.primaryShortlist()) {
                appendShortlistEntry(sb, r, "GREEN", "high", "yesterday high + ");
            }
            sb.append("\n");
        }
    }

    private void appendBearShortlist(StringBuilder sb, ElderSummaryReport report) {
        sb.append("## Primary Bear Shortlist — W1 RED + D1 RED + D1 QUIET\n");
        sb.append("*Weekly EMA falling (strategic bear) + daily EMA and MACD-H both falling (inertia and momentum in gear, downward) + market quiet (low slippage entry). Go short. Cover the moment a single daily indicator stops falling.*\n\n");
        if (report.primaryBearShortlist().isEmpty()) {
            sb.append("None tonight — inertia and momentum not in gear to the downside.\n\n");
        } else {
            for (ScanResult r : report.primaryBearShortlist()) {
                appendShortlistEntry(sb, r, "RED", "low", "yesterday low - ");
            }
            sb.append("\n");
        }
    }

    private void appendFreshSignalObservation(StringBuilder sb, List<ScanResult> bullList, List<ScanResult> bearList) {
        List<ScanResult> combined = Stream.concat(bullList.stream(), bearList.stream()).toList();
        if (combined.isEmpty()) return;
        long freshCount = combined.stream()
                .filter(r -> r.matchedIndicators().stream()
                        .anyMatch(mi -> mi instanceof ElderImpulseMatch e &&
                                e.timeframe() == Timeframe.D1 &&
                                e.daysSinceChange() <= FRESH_SIGNAL_DAYS))
                .count();
        if (freshCount == 0) return;
        if (freshCount == combined.size()) {
            sb.append("*⚠️ All shortlist entries are fresh signals — market just beginning to move, not mid-trend. " +
                      "Signals have not yet proven they will hold. Size conservatively until signals age past day 3.*\n\n");
        } else if (freshCount * 2 >= combined.size()) {
            sb.append(String.format("*%d of %d shortlist entries are fresh signals — early-stage move. " +
                      "Weight toward older signals for initial entries.*\n\n", freshCount, combined.size()));
        }
    }

    private void appendSpikeList(StringBuilder sb, List<ScanResult> spikes) {
        for (ScanResult r : spikes) {
            // Spike signals are D1 — force the link to the daily chart regardless of what
            // highestTimeframe() picked when W1 was also in the scan conditions.
            String d1Url = r.tradingviewUrl() != null
                    ? r.tradingviewUrl().replaceAll("interval=[^&]*", "interval=1D")
                    : null;
            r.matchedIndicators().stream()
                    .filter(mi -> mi instanceof ThermometerMatch)
                    .map(mi -> (ThermometerMatch) mi)
                    .findFirst()
                    .ifPresent(t -> {
                        if (t.temperatureEma() == null || t.temperatureEma().compareTo(BigDecimal.ZERO) == 0) {
                            return;
                        }
                        sb.append(String.format(
                                "- [%s](%s): temp=%s, ema=%s (%.1f× normal) (Vol: %s)\n",
                                r.assetSymbol(), d1Url,
                                formatTemp(t.temperature()),
                                formatTemp(t.temperatureEma()),
                                t.temperature().doubleValue() / t.temperatureEma().doubleValue(),
                                formatVolume(r.avgVolume7d())));
                    });
        }
    }

    private void appendShortlistEntry(StringBuilder sb, ScanResult r, String direction,
                                       String targetAnchor, String targetPrefix) {
        String d1Url = r.tradingviewUrl() != null
                ? r.tradingviewUrl().replaceAll("interval=[^&]*", "interval=1D")
                : null;
        sb.append(String.format("- [%s](%s)", r.assetSymbol(), d1Url));
        for (var mi : r.matchedIndicators()) {
            if (mi instanceof ElderImpulseMatch e) {
                sb.append(String.format("  %s %s for %d day(s)", e.timeframe(), direction, e.daysSinceChange()));
                if (e.timeframe().name().equals("D1") && e.daysSinceChange() <= FRESH_SIGNAL_DAYS) {
                    sb.append(" ⚡ *fresh — size conservatively, exit rule active from day 1*");
                }
            }
            if (mi instanceof ThermometerMatch t) {
                boolean insideBar = t.temperature().compareTo(java.math.BigDecimal.ZERO) == 0;
                if (insideBar) {
                    sb.append("  temp=0 *(inside bar — no range extension today; indecision, not low volatility. Valid entry but size conservatively.)*");
                } else {
                    boolean tempBelowEma = t.temperature().compareTo(t.temperatureEma()) < 0;
                    sb.append(String.format("  temp=%s ema=%s", formatTemp(t.temperature()), formatTemp(t.temperatureEma())));
                    if (tempBelowEma) {
                        sb.append(" *(today quieter than average — confirms calm entry)*");
                    }
                }
                sb.append(String.format("  → target: %s%s *(EMA = expected next-bar range, not today's temp)*",
                        targetPrefix, formatTemp(t.temperatureEma())));
            }
        }
        sb.append(String.format("  (Vol: %s)\n", formatVolume(r.avgVolume7d())));
    }

    private void appendBreadthConclusion(StringBuilder sb, MarketPulseDto w1Pulse, MarketPulseDto thermPulse) {
        if (w1Pulse == null) return;
        double w1Ratio = w1Pulse.bullishRatio().doubleValue();
        String w1Read;
        if (w1Ratio >= 0.5) {
            w1Read = "Macro environment is bullish — work primarily from the long side.";
        } else if (w1Ratio >= 0.3) {
            w1Read = "Macro environment is mixed — be selective, both sides have setups.";
        } else {
            w1Read = "Macro environment is bearish — bias to the short side or stand aside.";
        }
        String thermRead = "";
        if (thermPulse != null) {
            double quietRatio = thermPulse.bullishRatio().doubleValue();
            if (quietRatio >= 0.6) {
                thermRead = " Entry conditions are calm across most of the universe tonight.";
            } else {
                thermRead = " Market is running hot broadly — be selective on entry timing.";
            }
        }
        sb.append(String.format("> %s%s\n", w1Read, thermRead));
    }

    private static final int W1_FLIP_CAP = 10;

    private void appendW1Flips(StringBuilder sb, List<ScanResult> flips, String direction) {
        if (flips.isEmpty()) {
            sb.append(String.format("No fresh W1 %s flips this week.\n\n", direction.toLowerCase()));
            return;
        }
        List<ScanResult> sorted = flips.stream()
                .filter(r -> r.avgVolume7d() != null)
                .sorted((a, b) -> b.avgVolume7d().compareTo(a.avgVolume7d()))
                .toList();
        int total = sorted.size();
        List<ScanResult> shown = sorted.stream().limit(W1_FLIP_CAP).toList();
        for (ScanResult r : shown) {
            r.matchedIndicators().stream()
                    .filter(mi -> mi instanceof ElderImpulseMatch)
                    .map(mi -> (ElderImpulseMatch) mi)
                    .findFirst()
                    .ifPresent(e -> sb.append(String.format("- [%s](%s): W1 flipped %s %d day(s) ago (Vol: %s)\n",
                            r.assetSymbol(), r.tradingviewUrl(), direction, e.daysSinceChange(),
                            formatVolume(r.avgVolume7d()))));
        }
        if (total > W1_FLIP_CAP) {
            sb.append(String.format("*+ %d more — showing top %d by volume*\n", total - W1_FLIP_CAP, W1_FLIP_CAP));
        }
        sb.append("\n");
    }

    private void appendPulseLine(StringBuilder sb, String label, MarketPulseDto pulse,
                                  String bullishLabel, String bearishLabel, String neutralLabel) {
        appendPulseLine(sb, label, pulse, bullishLabel, bearishLabel, neutralLabel, null);
    }

    private void appendPulseLine(StringBuilder sb, String label, MarketPulseDto pulse,
                                  String bullishLabel, String bearishLabel, String neutralLabel,
                                  String traderNote) {
        if (pulse == null) {
            sb.append(String.format("- **%s:** no data\n", label));
            return;
        }
        double bearishPct = pulse.totalAssets() > 0
                ? (double) pulse.bearishCount() / pulse.totalAssets() * 100 : 0;
        sb.append(String.format("- **%s:** %d %s / %d %s / %d %s  (%.0f%% %s / %.0f%% %s)",
                label,
                pulse.bullishCount(), bullishLabel,
                pulse.bearishCount(), bearishLabel,
                pulse.missingCount(), neutralLabel,
                pulse.bullishRatio().doubleValue() * 100, bullishLabel,
                bearishPct, bearishLabel));
        if (traderNote != null) {
            sb.append("  ").append(traderNote);
        }
        sb.append("\n");
    }

    private void appendSignals(StringBuilder sb, List<SignalStateDto> signals) {
        List<SignalStateDto> withFlip = signals.stream()
                .filter(s -> s.daysSinceFlip() != null)
                .collect(Collectors.toList());
        if (withFlip.isEmpty()) {
            sb.append("None found.\n\n");
        } else {
            for (SignalStateDto s : withFlip) {
                sb.append(String.format("- [%s](%s): Flipped %d days ago (%s)\n",
                        s.symbol(), s.tradingviewUrl(), s.daysSinceFlip(), formatVolAndPct(s.avgVolume7d(), s.pctChangeSinceFlip())));
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
        return value.round(new java.math.MathContext(3, java.math.RoundingMode.HALF_UP))
                    .stripTrailingZeros().toPlainString();
    }

    private String formatVolAndPct(BigDecimal volume, BigDecimal pctChangeSinceFlip) {
        String pctStr = formatPctChange(pctChangeSinceFlip);
        return pctStr != null
                ? String.format("Vol: %s, %s since flip", formatVolume(volume), pctStr)
                : String.format("Vol: %s", formatVolume(volume));
    }

    private String formatPctChange(BigDecimal pctChangeSinceFlip) {
        if (pctChangeSinceFlip == null) return null;
        String sign = pctChangeSinceFlip.signum() >= 0 ? "+" : "";
        return sign + pctChangeSinceFlip.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + "%";
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
