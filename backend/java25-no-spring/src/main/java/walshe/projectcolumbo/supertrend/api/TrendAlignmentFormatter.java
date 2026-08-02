package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.signal.SignalSummary;
import walshe.projectcolumbo.supertrend.signal.TrendAlignment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Renders a {@link TrendAlignment} as {@code text/markdown} or a plain-text watchlist. */
public final class TrendAlignmentFormatter {

    private TrendAlignmentFormatter() {
    }

    public static String toMarkdown(TrendAlignment alignment, Clock clock) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        StringBuilder markdown = new StringBuilder("# SuperTrend Trend Alignment\n\n");
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
            markdown.append("- ").append(entry.symbol()).append(": D1 ").append(verb).append(' ').append(recency(entry, now));
            String pct = formatPctChange(entry.pctChangeSinceFlip());
            if (pct != null) {
                markdown.append(" (").append(pct).append(" since flip)");
            }
            markdown.append('\n');
        }
        markdown.append('\n');
    }

    public static String toWatchlist(TrendAlignment alignment) {
        StringBuilder watchlist = new StringBuilder();
        appendWatchlistSection(watchlist, "Bullish Confluence", alignment.bullishConfluence());
        appendWatchlistSection(watchlist, "Bullish Retest", alignment.bullishRetest());
        appendWatchlistSection(watchlist, "Bearish Confluence", alignment.bearishConfluence());
        appendWatchlistSection(watchlist, "Bearish Retest", alignment.bearishRetest());
        return watchlist.toString();
    }

    private static void appendWatchlistSection(StringBuilder watchlist, String header, List<SignalSummary> entries) {
        if (entries.isEmpty()) {
            return;
        }
        watchlist.append("### ").append(header).append('\n');
        for (SignalSummary entry : entries) {
            watchlist.append(entry.symbol()).append('\n');
        }
    }

    private static String recency(SignalSummary entry, OffsetDateTime now) {
        if (entry.lastFlipTime() == null) {
            return "established";
        }
        long days = ChronoUnit.DAYS.between(entry.lastFlipTime().toLocalDate(), now.toLocalDate());
        return days + " day(s) ago";
    }

    private static String formatPctChange(BigDecimal pctChangeSinceFlip) {
        if (pctChangeSinceFlip == null) {
            return null;
        }
        String sign = pctChangeSinceFlip.signum() >= 0 ? "+" : "";
        return sign + pctChangeSinceFlip + "%";
    }
}
