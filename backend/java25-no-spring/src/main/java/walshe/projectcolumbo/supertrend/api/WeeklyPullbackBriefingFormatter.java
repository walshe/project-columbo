package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.shared.AssetClass;

import java.time.OffsetDateTime;
import java.util.List;

/** Renders a {@link WeeklyPullbackBriefingReport} as {@code text/markdown} - the only format this briefing produces. */
public final class WeeklyPullbackBriefingFormatter {

    private WeeklyPullbackBriefingFormatter() {
    }

    public static String toMarkdown(WeeklyPullbackBriefingReport report, OffsetDateTime now) {
        StringBuilder md = new StringBuilder("# Weekly Pullback Briefing\n\n");
        md.append("**Generated at:** ").append(now).append("\n\n");
        md.append("**Ingestion run:** #").append(report.ingestionRunId()).append(" - ").append(report.ingestionStatus()).append("\n\n");
        WeeklyBriefingFormatting.appendRegimeSection(md, report.regimePulses());
        WeeklyBriefingFormatting.appendBtcSection(md, report.btcW1State(), report.btcD1State(), report.btcAligned());
        for (AssetClass assetClass : WeeklyBriefingFormatting.BRIEFING_ASSET_CLASSES) {
            appendClassSection(md, report, assetClass, now);
        }
        return md.toString();
    }

    private static void appendClassSection(StringBuilder md, WeeklyPullbackBriefingReport report, AssetClass assetClass, OffsetDateTime now) {
        md.append("## ").append(assetClass).append("\n\n");
        boolean showBearish = assetClass == AssetClass.CRYPTO;

        WeeklyBriefingFormatting.appendSignalList(md, "Bullish Pullback (W1 bullish, D1 recently flipped bearish)",
                report.bullishPullbackCandidates().getOrDefault(assetClass, List.of()), now);
        if (showBearish) {
            WeeklyBriefingFormatting.appendSignalList(md, "Bearish Bounce (W1 bearish, D1 recently flipped bullish)",
                    report.bearishPullbackCandidates().getOrDefault(assetClass, List.of()), now);
        }

        WeeklyBriefingFormatting.appendScanList(md, "Bullish Pullback Scan Candidates (top liquidity, deepest dip first)",
                report.bullishScanCandidates().getOrDefault(assetClass, List.of()));
        if (showBearish) {
            WeeklyBriefingFormatting.appendScanList(md, "Bearish Bounce Scan Candidates (top liquidity, biggest bounce first)",
                    report.bearishScanCandidates().getOrDefault(assetClass, List.of()));
        }
    }
}
