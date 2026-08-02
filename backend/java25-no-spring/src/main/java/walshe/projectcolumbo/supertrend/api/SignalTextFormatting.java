package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.signal.SignalSummary;

import java.math.BigDecimal;
import java.util.List;

/** Small rendering helpers shared by the Markdown/Watchlist formatters ({@link TrendAlignmentFormatter}, {@link SummaryFormatter}). */
final class SignalTextFormatting {

    private SignalTextFormatting() {
    }

    static String formatPctChange(BigDecimal pctChangeSinceFlip) {
        if (pctChangeSinceFlip == null) {
            return null;
        }
        String sign = pctChangeSinceFlip.signum() >= 0 ? "+" : "";
        return sign + pctChangeSinceFlip + "%";
    }

    /** Omits the section entirely (no header) when {@code entries} is empty, rather than emitting a stray header with no body. */
    static void appendWatchlistSection(StringBuilder watchlist, String header, List<SignalSummary> entries) {
        if (entries.isEmpty()) {
            return;
        }
        watchlist.append("### ").append(header).append('\n');
        for (SignalSummary entry : entries) {
            watchlist.append(entry.symbol()).append('\n');
        }
    }
}
