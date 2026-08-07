package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.pulse.MarketBreadthSnapshot;
import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.signal.ScanConditionMatch;
import walshe.projectcolumbo.supertrend.signal.ScanResult;
import walshe.projectcolumbo.supertrend.signal.SignalSummary;
import walshe.projectcolumbo.supertrend.signal.TrendAlignment;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

/** Renders a {@link WeeklyTrendBriefingReport} as {@code text/markdown} - the only format this briefing produces. */
public final class WeeklyTrendBriefingFormatter {

    private static final int RATIO_TO_PERCENT_SCALE = 2;
    private static final List<AssetClass> BRIEFING_ASSET_CLASSES = List.of(AssetClass.CRYPTO, AssetClass.ETF, AssetClass.STOCK);

    private WeeklyTrendBriefingFormatter() {
    }

    public static String toMarkdown(WeeklyTrendBriefingReport report, OffsetDateTime now) {
        StringBuilder md = new StringBuilder("# Weekly Trend Briefing\n\n");
        md.append("**Generated at:** ").append(now).append("\n\n");
        md.append("**Ingestion run:** #").append(report.ingestionRunId()).append(" - ").append(report.ingestionStatus()).append("\n\n");
        appendRegimeSection(md, report);
        appendBtcSection(md, report);
        for (AssetClass assetClass : BRIEFING_ASSET_CLASSES) {
            appendClassSection(md, report, assetClass, now);
        }
        return md.toString();
    }

    private static void appendRegimeSection(StringBuilder md, WeeklyTrendBriefingReport report) {
        md.append("## Regime Read (W1 market breadth)\n\n");
        for (AssetClass assetClass : BRIEFING_ASSET_CLASSES) {
            MarketBreadthSnapshot pulse = report.regimePulses().get(assetClass);
            if (pulse == null) {
                md.append("- **").append(assetClass).append(":** no snapshot yet\n");
                continue;
            }
            BigDecimal bullishPercent = pulse.bullishRatio().multiply(BigDecimal.valueOf(100)).setScale(RATIO_TO_PERCENT_SCALE, RoundingMode.HALF_UP);
            md.append("- **").append(assetClass).append(":** ")
                    .append(pulse.bullishCount()).append(" bullish / ").append(pulse.bearishCount()).append(" bearish (")
                    .append(bullishPercent).append("% bullish)\n");
        }
        md.append('\n');
    }

    private static void appendBtcSection(StringBuilder md, WeeklyTrendBriefingReport report) {
        md.append("## BTC Alignment (crypto tiebreaker)\n\n")
                .append("- **W1:** ").append(stateOrNoData(report.btcW1State())).append('\n')
                .append("- **D1:** ").append(stateOrNoData(report.btcD1State())).append('\n')
                .append("- **Aligned:** ").append(report.btcAligned()
                        ? "yes - treat as crypto's directional bias this week"
                        : "no - caution on crypto trades regardless of breadth")
                .append("\n\n");
    }

    private static String stateOrNoData(TrendState state) {
        return state != null ? state.toString() : "no data";
    }

    private static void appendClassSection(StringBuilder md, WeeklyTrendBriefingReport report, AssetClass assetClass, OffsetDateTime now) {
        md.append("## ").append(assetClass).append("\n\n");
        TrendAlignment alignment = report.trendAlignments().get(assetClass);
        boolean showBearish = assetClass == AssetClass.CRYPTO;

        appendConfluenceList(md, "Bullish Confluence (W1+D1)", alignment.bullishConfluence(), now);
        appendConfluenceList(md, "Bullish Retest (W1 bullish, D1 recently flipped bearish)", alignment.bullishRetest(), now);
        if (showBearish) {
            appendConfluenceList(md, "Bearish Confluence (W1+D1)", alignment.bearishConfluence(), now);
            appendConfluenceList(md, "Bearish Retest (W1 bearish, D1 recently flipped bullish)", alignment.bearishRetest(), now);
        }

        appendScanList(md, "Bullish Scan Candidates (top liquidity, ranked by D1 movement since flip)", report.bullishScanCandidates().getOrDefault(assetClass, List.of()));
        if (showBearish) {
            appendScanList(md, "Bearish Scan Candidates (top liquidity, ranked by D1 movement since flip)", report.bearishScanCandidates().getOrDefault(assetClass, List.of()));
        }
    }

    private static void appendConfluenceList(StringBuilder md, String header, List<SignalSummary> entries, OffsetDateTime now) {
        md.append("### ").append(header).append('\n');
        if (entries.isEmpty()) {
            md.append("_None found._\n\n");
            return;
        }
        for (SignalSummary entry : entries) {
            md.append("- ").append(SignalTextFormatting.symbolMarkdown(entry))
                    .append(": ").append(recencyClause(entry.lastFlipTime() != null ? entry.daysSinceFlip(now) : null));
            String pct = SignalTextFormatting.formatPctChange(entry.pctChangeSinceFlip());
            if (pct != null) {
                md.append(" (").append(pct).append(" since flip)");
            }
            md.append('\n');
        }
        md.append('\n');
    }

    /** Some assets have never flipped since being onboarded - no recorded flip event to report an age for. */
    private static String recencyClause(Long daysSinceFlip) {
        return daysSinceFlip != null ? "flipped " + daysSinceFlip + " day(s) ago" : "no flip on record (established)";
    }

    private static void appendScanList(StringBuilder md, String header, List<ScanResult> results) {
        md.append("### ").append(header).append('\n');
        if (results.isEmpty()) {
            md.append("_None found._\n\n");
            return;
        }
        for (ScanResult result : results) {
            md.append("- **").append(result.symbol()).append("** (avg 7d volume: ").append(formatVolume(result.avgVolume7d())).append(")\n");
            for (ScanConditionMatch match : result.matchedConditions()) {
                md.append("  - ").append(match.timeframe()).append(' ').append(match.state())
                        .append(", ").append(recencyClause(match.daysSinceFlip()));
                String pct = SignalTextFormatting.formatPctChange(match.pctChangeSinceFlip());
                if (pct != null) {
                    md.append(" (").append(pct).append(" since flip)");
                }
                if (match.tradingviewUrl() != null) {
                    md.append(" - [chart](").append(match.tradingviewUrl()).append(')');
                }
                md.append('\n');
            }
        }
        md.append('\n');
    }

    /** A fresh instance per call - {@link NumberFormat} is not thread-safe, so no shared static instance. */
    private static String formatVolume(BigDecimal volume) {
        return NumberFormat.getIntegerInstance(Locale.US).format(volume);
    }
}
