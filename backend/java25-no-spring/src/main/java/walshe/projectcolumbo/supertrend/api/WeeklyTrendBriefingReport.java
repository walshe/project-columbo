package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.pipeline.IngestionRunStatus;
import walshe.projectcolumbo.supertrend.pulse.MarketBreadthSnapshot;
import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.signal.ProvisionalTrendResult;
import walshe.projectcolumbo.supertrend.signal.ScanResult;
import walshe.projectcolumbo.supertrend.signal.TrendAlignment;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Composed output of the weekly trend briefing: the ingestion run that fed it, W1
 * market-breadth pulse per asset class, BTCUSDT's W1/D1 alignment (the crypto direction
 * tiebreaker), per-class trend-alignment/scan results, and a preview of names not yet
 * confluence-eligible whose provisional W1 read now agrees with their committed D1 state.
 *
 * @param regimePulses           W1 market-breadth snapshot per asset class; a missing/null value means no snapshot exists yet
 * @param btcW1State             BTCUSDT's W1 trend state; null if BTCUSDT has no recorded state yet
 * @param btcD1State             BTCUSDT's D1 trend state; null if BTCUSDT has no recorded state yet
 * @param btcW1Provisional       BTCUSDT's provisional W1 read; null if not enough data yet to compute one
 * @param trendAlignments        W1+D1 confluence/retest per asset class - built entirely from committed data, never touched by provisional reads
 * @param bullishScanCandidates  W1+D1 bullish scan matches per asset class, sorted by liquidity
 * @param bearishScanCandidates  W1+D1 bearish scan matches, sorted by liquidity - CRYPTO only, since stocks/ETFs are longs-only
 * @param flipsForming           per asset class, names not currently confluence-eligible whose provisional W1 now agrees with committed D1 - kept structurally separate from the confirmed lists above, never blended into them
 */
public record WeeklyTrendBriefingReport(
        long ingestionRunId,
        IngestionRunStatus ingestionStatus,
        Map<AssetClass, MarketBreadthSnapshot> regimePulses,
        TrendState btcW1State,
        TrendState btcD1State,
        ProvisionalTrendResult btcW1Provisional,
        Map<AssetClass, TrendAlignment> trendAlignments,
        Map<AssetClass, List<ScanResult>> bullishScanCandidates,
        Map<AssetClass, List<ScanResult>> bearishScanCandidates,
        Map<AssetClass, List<FlipForming>> flipsForming
) {
    public WeeklyTrendBriefingReport {
        Objects.requireNonNull(ingestionStatus, "ingestionStatus must not be null");
        Objects.requireNonNull(regimePulses, "regimePulses must not be null");
        Objects.requireNonNull(trendAlignments, "trendAlignments must not be null");
        Objects.requireNonNull(bullishScanCandidates, "bullishScanCandidates must not be null");
        Objects.requireNonNull(bearishScanCandidates, "bearishScanCandidates must not be null");
        Objects.requireNonNull(flipsForming, "flipsForming must not be null");
    }

    public boolean btcAligned() {
        return btcW1State != null && btcW1State == btcD1State;
    }
}
