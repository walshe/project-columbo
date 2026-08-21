package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.pulse.MarketBreadthSnapshot;
import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.signal.ProvisionalTrendResult;
import walshe.projectcolumbo.supertrend.signal.ScanConditionMatch;
import walshe.projectcolumbo.supertrend.signal.ScanResult;
import walshe.projectcolumbo.supertrend.signal.SignalSummary;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rendering helpers shared by every {@code weekly-*-briefing} formatter
 * ({@link WeeklyTrendBriefingFormatter}, {@link WeeklyPullbackBriefingFormatter}): the
 * regime-read and BTC-alignment sections are identical objective market context regardless of
 * which trading philosophy a given briefing is built around, so they're rendered once here
 * instead of duplicated per formatter.
 */
final class WeeklyBriefingFormatting {

    static final List<AssetClass> BRIEFING_ASSET_CLASSES = List.of(AssetClass.CRYPTO, AssetClass.ETF, AssetClass.STOCK);

    private static final int RATIO_TO_PERCENT_SCALE = 2;
    private static final int FLIP_LEVEL_SCALE = 4;

    private WeeklyBriefingFormatting() {
    }

    static void appendRegimeSection(StringBuilder md, Map<AssetClass, MarketBreadthSnapshot> regimePulses) {
        md.append("## Regime Read (W1 market breadth)\n\n");
        for (AssetClass assetClass : BRIEFING_ASSET_CLASSES) {
            MarketBreadthSnapshot pulse = regimePulses.get(assetClass);
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

    /**
     * @param btcW1Provisional BTC's provisional W1 read (see {@link ProvisionalTrendResult}); null
     *                         when there's not yet enough data to compute one - the line is omitted in that case
     */
    static void appendBtcSection(StringBuilder md, TrendState btcW1State, TrendState btcD1State, boolean btcAligned, ProvisionalTrendResult btcW1Provisional) {
        md.append("## BTC Alignment (crypto tiebreaker)\n\n")
                .append("- **W1:** ").append(stateOrNoData(btcW1State)).append('\n')
                .append("- **D1:** ").append(stateOrNoData(btcD1State)).append('\n')
                .append("- **Aligned:** ").append(btcAligned
                        ? "yes - treat as crypto's directional bias this week"
                        : "no - caution on crypto trades regardless of breadth")
                .append('\n');
        if (btcW1Provisional != null) {
            md.append("- **W1 provisional (as of today):** ").append(formatProvisional(btcW1Provisional)).append('\n');
        }
        md.append('\n');
    }

    private static String stateOrNoData(TrendState state) {
        return state != null ? state.toString() : "no data";
    }

    /** "BEARISH (flip level 123.45)" - shared wording for every place a provisional read is shown. */
    static String formatProvisional(ProvisionalTrendResult provisional) {
        BigDecimal flipLevel = provisional.flipLevel().setScale(FLIP_LEVEL_SCALE, RoundingMode.HALF_UP);
        return provisional.direction() + " (flip level " + flipLevel.toPlainString() + ")";
    }

    /** @param divergingProvisional symbol -&gt; provisional read, only for entries whose provisional read differs from the committed direction this list represents; pass {@code Map.of()} for a formatter that keeps provisional data out of this list entirely */
    static void appendSignalList(StringBuilder md, String header, List<SignalSummary> entries, OffsetDateTime now, Map<String, ProvisionalTrendResult> divergingProvisional) {
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
            appendDivergingAnnotation(md, divergingProvisional, entry.symbol());
            md.append('\n');
        }
        md.append('\n');
    }

    /** @param divergingProvisional see {@link #appendSignalList} */
    static void appendScanList(StringBuilder md, String header, List<ScanResult> results, Map<String, ProvisionalTrendResult> divergingProvisional) {
        md.append("### ").append(header).append('\n');
        if (results.isEmpty()) {
            md.append("_None found._\n\n");
            return;
        }
        for (ScanResult result : results) {
            md.append("- **").append(result.symbol()).append("** (avg 7d volume: ").append(formatVolume(result.avgVolume7d())).append(")");
            appendDivergingAnnotation(md, divergingProvisional, result.symbol());
            md.append('\n');
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

    private static void appendDivergingAnnotation(StringBuilder md, Map<String, ProvisionalTrendResult> divergingProvisional, String symbol) {
        ProvisionalTrendResult provisional = divergingProvisional.get(symbol);
        if (provisional != null) {
            md.append(" - **watch: W1 provisional ").append(formatProvisional(provisional)).append("**");
        }
    }

    /** Some assets have never flipped since being onboarded - no recorded flip event to report an age for. */
    private static String recencyClause(Long daysSinceFlip) {
        return daysSinceFlip != null ? "flipped " + daysSinceFlip + " day(s) ago" : "no flip on record (established)";
    }

    /** A fresh instance per call - {@link NumberFormat} is not thread-safe, so no shared static instance. */
    private static String formatVolume(BigDecimal volume) {
        return NumberFormat.getIntegerInstance(Locale.US).format(volume);
    }
}
