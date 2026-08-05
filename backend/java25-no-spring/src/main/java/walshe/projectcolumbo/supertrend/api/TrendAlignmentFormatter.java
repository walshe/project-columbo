package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.signal.SignalSummary;
import walshe.projectcolumbo.supertrend.signal.TrendAlignment;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/** Renders a {@link TrendAlignment} as {@code text/markdown} or a plain-text watchlist. */
public final class TrendAlignmentFormatter {

    private TrendAlignmentFormatter() {
    }

    public static String toMarkdown(TrendAlignment alignment, int maxRetestAgeDays, AssetClass assetClass, Clock clock) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        StringBuilder markdown = new StringBuilder("# SuperTrend Trend Alignment\n\n");
        markdown.append("**Timeframes:** W1 + D1 (D1 driving)\n\n");
        markdown.append("**Max Retest Age:** ").append(maxRetestAgeDays).append(" day(s)\n\n");
        if (assetClass != null) {
            markdown.append("**Asset Class:** ").append(assetClass).append("\n\n");
        }
        appendMarkdownSection(markdown, "Bullish Confluence (W1 + D1)", alignment.bullishConfluence(), "aligned", now);
        appendMarkdownSection(markdown, "Bullish Retest (W1 bullish, D1 recently bearish)", alignment.bullishRetest(), "flipped", now);
        appendMarkdownSection(markdown, "Bearish Confluence (W1 + D1)", alignment.bearishConfluence(), "aligned", now);
        appendMarkdownSection(markdown, "Bearish Retest (W1 bearish, D1 recently bullish)", alignment.bearishRetest(), "flipped", now);
        return markdown.toString();
    }

    private static void appendMarkdownSection(StringBuilder markdown, String header, List<SignalSummary> entries, String verb, OffsetDateTime now) {
        markdown.append("## ").append(header).append("\n");
        if (entries.isEmpty()) {
            markdown.append("_None._\n\n");
            return;
        }
        for (SignalSummary entry : entries) {
            markdown.append("- ").append(SignalTextFormatting.symbolMarkdown(entry)).append(": D1 ").append(verb).append(' ').append(recency(entry, now));
            String pct = SignalTextFormatting.formatPctChange(entry.pctChangeSinceFlip());
            if (pct != null) {
                markdown.append(" (").append(pct).append(" since flip)");
            }
            markdown.append('\n');
        }
        markdown.append('\n');
    }

    public static String toWatchlist(TrendAlignment alignment) {
        StringBuilder watchlist = new StringBuilder();
        SignalTextFormatting.appendWatchlistSection(watchlist, "Bullish Confluence", alignment.bullishConfluence());
        SignalTextFormatting.appendWatchlistSection(watchlist, "Bullish Retest", alignment.bullishRetest());
        SignalTextFormatting.appendWatchlistSection(watchlist, "Bearish Confluence", alignment.bearishConfluence());
        SignalTextFormatting.appendWatchlistSection(watchlist, "Bearish Retest", alignment.bearishRetest());
        return watchlist.toString();
    }

    private static String recency(SignalSummary entry, OffsetDateTime now) {
        if (entry.lastFlipTime() == null) {
            return "established";
        }
        return entry.daysSinceFlip(now) + " day(s) ago";
    }
}
