package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.signal.TrendAlignment;

import java.time.OffsetDateTime;
import java.util.List;

/** Renders a {@link WeeklyTrendBriefingReport} as {@code text/markdown} - the only format this briefing produces. */
public final class WeeklyTrendBriefingFormatter {

    private WeeklyTrendBriefingFormatter() {
    }

    public static String toMarkdown(WeeklyTrendBriefingReport report, OffsetDateTime now) {
        StringBuilder md = new StringBuilder("# Weekly Trend Briefing\n\n");
        md.append("**Generated at:** ").append(now).append("\n\n");
        md.append("**Ingestion run:** #").append(report.ingestionRunId()).append(" - ").append(report.ingestionStatus()).append("\n\n");
        WeeklyBriefingFormatting.appendRegimeSection(md, report.regimePulses());
        WeeklyBriefingFormatting.appendBtcSection(md, report.btcW1State(), report.btcD1State(), report.btcAligned());
        for (AssetClass assetClass : WeeklyBriefingFormatting.BRIEFING_ASSET_CLASSES) {
            appendClassSection(md, report, assetClass, now);
        }
        return md.toString();
    }

    private static void appendClassSection(StringBuilder md, WeeklyTrendBriefingReport report, AssetClass assetClass, OffsetDateTime now) {
        md.append("## ").append(assetClass).append("\n\n");
        TrendAlignment alignment = report.trendAlignments().get(assetClass);
        boolean showBearish = assetClass == AssetClass.CRYPTO;

        WeeklyBriefingFormatting.appendSignalList(md, "Bullish Confluence (W1+D1)", alignment.bullishConfluence(), now);
        WeeklyBriefingFormatting.appendSignalList(md, "Bullish Retest (W1 bullish, D1 recently flipped bearish)", alignment.bullishRetest(), now);
        if (showBearish) {
            WeeklyBriefingFormatting.appendSignalList(md, "Bearish Confluence (W1+D1)", alignment.bearishConfluence(), now);
            WeeklyBriefingFormatting.appendSignalList(md, "Bearish Retest (W1 bearish, D1 recently flipped bullish)", alignment.bearishRetest(), now);
        }

        WeeklyBriefingFormatting.appendScanList(md, "Bullish Scan Candidates (top liquidity, ranked by D1 movement since flip)", report.bullishScanCandidates().getOrDefault(assetClass, List.of()));
        if (showBearish) {
            WeeklyBriefingFormatting.appendScanList(md, "Bearish Scan Candidates (top liquidity, ranked by D1 movement since flip)", report.bearishScanCandidates().getOrDefault(assetClass, List.of()));
        }
    }
}
