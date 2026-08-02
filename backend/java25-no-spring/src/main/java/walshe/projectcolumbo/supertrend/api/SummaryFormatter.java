package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.pulse.MarketBreadthSnapshot;
import walshe.projectcolumbo.supertrend.signal.SignalSummary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/** Renders a {@link SummaryResponse} as {@code text/markdown} or a plain-text watchlist. */
public final class SummaryFormatter {

    private static final int RATIO_TO_PERCENT_SCALE = 2;

    private SummaryFormatter() {
    }

    public static String toMarkdown(SummaryResponse response, Clock clock) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        StringBuilder markdown = new StringBuilder("# Market Summary Report\n\n");
        appendPulseSection(markdown, response.pulse());
        appendFlipsSection(markdown, "Recent Bullish Flips", response.bullishSignals(), now);
        appendFlipsSection(markdown, "Recent Bearish Flips", response.bearishSignals(), now);
        return markdown.toString();
    }

    private static void appendPulseSection(StringBuilder markdown, MarketBreadthSnapshot pulse) {
        // Matches the old app: the whole section (including its header) is omitted, not shown
        // with a placeholder, when no snapshot exists yet for this timeframe.
        if (pulse == null) {
            return;
        }
        BigDecimal bullishRatioPercent = pulse.bullishRatio().multiply(BigDecimal.valueOf(100)).setScale(RATIO_TO_PERCENT_SCALE, RoundingMode.HALF_UP);
        markdown.append("## Market Pulse\n")
                .append("- **Bullish:** ").append(pulse.bullishCount()).append('\n')
                .append("- **Bearish:** ").append(pulse.bearishCount()).append('\n')
                .append("- **Bullish Ratio:** ").append(bullishRatioPercent).append("%\n\n");
    }

    private static void appendFlipsSection(StringBuilder markdown, String header, List<SignalSummary> signals, OffsetDateTime now) {
        markdown.append("## ").append(header).append('\n');
        List<SignalSummary> withFlip = signals.stream().filter(s -> s.lastFlipTime() != null).toList();
        if (withFlip.isEmpty()) {
            markdown.append("_None found._\n\n");
            return;
        }
        for (SignalSummary signal : withFlip) {
            markdown.append("- ").append(signal.symbol()).append(": Flipped ")
                    .append(SignalTextFormatting.daysSince(signal.lastFlipTime(), now)).append(" day(s) ago");
            String pct = SignalTextFormatting.formatPctChange(signal.pctChangeSinceFlip());
            if (pct != null) {
                markdown.append(" (").append(pct).append(" since flip)");
            }
            markdown.append('\n');
        }
        markdown.append('\n');
    }

    public static String toWatchlist(SummaryResponse response) {
        StringBuilder watchlist = new StringBuilder();
        SignalTextFormatting.appendWatchlistSection(watchlist, "Recent Bullish Flips", withFlipOnly(response.bullishSignals()));
        SignalTextFormatting.appendWatchlistSection(watchlist, "Recent Bearish Flips", withFlipOnly(response.bearishSignals()));
        return watchlist.toString();
    }

    private static List<SignalSummary> withFlipOnly(List<SignalSummary> signals) {
        return signals.stream().filter(s -> s.lastFlipTime() != null).toList();
    }
}
