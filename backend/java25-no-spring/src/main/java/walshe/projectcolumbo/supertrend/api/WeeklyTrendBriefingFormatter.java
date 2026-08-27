package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.signal.TrendAlignment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Renders a {@link WeeklyTrendBriefingReport} as {@code text/markdown} - the only format this briefing produces. */
public final class WeeklyTrendBriefingFormatter {

    private WeeklyTrendBriefingFormatter() {
    }

    public static String toMarkdown(WeeklyTrendBriefingReport report, OffsetDateTime now) {
        StringBuilder md = new StringBuilder("# Weekly Trend Briefing\n\n");
        md.append("**Generated at:** ").append(now).append("\n\n");
        md.append("**Ingestion run:** #").append(report.ingestionRunId()).append(" - ").append(report.ingestionStatus()).append("\n\n");
        WeeklyBriefingFormatting.appendRegimeSection(md, report.regimePulses());
        WeeklyBriefingFormatting.appendBtcSection(md, report.btcW1State(), report.btcD1State(), report.btcAligned(), report.btcW1Provisional());
        for (AssetClass assetClass : WeeklyBriefingFormatting.BRIEFING_ASSET_CLASSES) {
            appendClassSection(md, report, assetClass, now);
        }
        return md.toString();
    }

    private static void appendClassSection(StringBuilder md, WeeklyTrendBriefingReport report, AssetClass assetClass, OffsetDateTime now) {
        md.append("## ").append(assetClass).append("\n\n");
        TrendAlignment alignment = report.trendAlignments().get(assetClass);
        boolean showBearish = assetClass == AssetClass.CRYPTO;

        // Confirmed lists are built entirely from committed data - Map.of() keeps provisional
        // annotations out of them entirely, per this report's "only back what's already
        // confirmed" premise. Provisional data only ever appears in "Flips Forming" below.
        //
        // Retest is deliberately NOT rendered here even though TrendAlignment computes it
        // alongside confluence - it's the exact same data as the pullback briefing's headline
        // (same TrendAlignmentService call, same maxRetestAgeDays), and "W1 intact but D1
        // currently against it" is a pullback-briefing question, not a "what's already confirmed"
        // one. See openspec/changes/dedupe-weekly-briefing-retest/ for the full rationale - each
        // report now owns exactly one thesis instead of both showing the same list.
        WeeklyBriefingFormatting.appendSignalList(md, "Bullish Confluence (W1+D1)", alignment.bullishConfluence(), now, Map.of());
        if (showBearish) {
            WeeklyBriefingFormatting.appendSignalList(md, "Bearish Confluence (W1+D1)", alignment.bearishConfluence(), now, Map.of());
        }

        WeeklyBriefingFormatting.appendScanList(md, "Bullish Scan Candidates (top liquidity, ranked by D1 movement since flip)", report.bullishScanCandidates().getOrDefault(assetClass, List.of()), Map.of());
        if (showBearish) {
            WeeklyBriefingFormatting.appendScanList(md, "Bearish Scan Candidates (top liquidity, ranked by D1 movement since flip)", report.bearishScanCandidates().getOrDefault(assetClass, List.of()), Map.of());
        }

        appendFlipsForming(md, report.flipsForming().getOrDefault(assetClass, List.of()));
    }

    private static void appendFlipsForming(StringBuilder md, List<FlipForming> forming) {
        md.append("### Flips Forming (not yet confluence-eligible, provisional W1 now agrees with committed D1)\n");
        if (forming.isEmpty()) {
            md.append("_None found._\n\n");
            return;
        }
        for (FlipForming entry : forming) {
            String symbolText = SignalTextFormatting.symbolMarkdown(entry.symbol(), entry.name(), entry.tradingviewUrl());
            md.append("- ").append(symbolText).append(": ").append(WeeklyBriefingFormatting.formatProvisional(entry.provisional())).append('\n');
        }
        md.append('\n');
    }
}
