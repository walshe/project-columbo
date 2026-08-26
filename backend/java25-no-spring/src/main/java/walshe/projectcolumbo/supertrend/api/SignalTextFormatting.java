package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.shared.TradingViewUrl;
import walshe.projectcolumbo.supertrend.signal.SignalSummary;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

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

    /** Markdown link target for an entry: its TradingView chart, falling back to plain (unlinked) symbol text if unavailable. */
    static String symbolMarkdown(SignalSummary entry) {
        return symbolMarkdown(entry.symbol(), entry.name(), entry.tradingviewUrl());
    }

    /**
     * Link (or plain) text for a symbol: "Name (SYMBOL)" when a friendly display name is known,
     * falling back to the bare symbol when it isn't - wrapped in a Markdown link to
     * {@code tradingviewUrl} when that's available, plain text otherwise.
     */
    static String symbolMarkdown(String symbol, String name, String tradingviewUrl) {
        String label = name != null ? name + " (" + symbol + ")" : symbol;
        return tradingviewUrl != null ? "[" + label + "](" + tradingviewUrl + ")" : label;
    }

    /**
     * Omits the section entirely (no header) when there's nothing to show - either because
     * {@code entries} is empty, or because every entry lacks a TradingView watchlist token (each
     * entry without one is dropped, not shown under a fallback plain symbol - a watchlist is
     * specifically a list of tokens a charting tool can import).
     */
    static void appendWatchlistSection(StringBuilder watchlist, String header, List<SignalSummary> entries) {
        List<String> tokens = entries.stream()
                .map(entry -> TradingViewUrl.watchlistSymbol(entry.tradingviewUrl()))
                .filter(Objects::nonNull)
                .toList();
        if (tokens.isEmpty()) {
            return;
        }
        watchlist.append("### ").append(header).append('\n');
        for (String token : tokens) {
            watchlist.append(token).append('\n');
        }
    }
}
