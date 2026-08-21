package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.pipeline.IngestionRunStatus;
import walshe.projectcolumbo.supertrend.pulse.MarketBreadthSnapshot;
import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.signal.ProvisionalTrendResult;
import walshe.projectcolumbo.supertrend.signal.ScanResult;
import walshe.projectcolumbo.supertrend.signal.SignalSummary;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Composed output of the weekly pullback briefing: the ingestion run that fed it, W1
 * market-breadth pulse per asset class, BTCUSDT's W1/D1 alignment (the crypto direction
 * tiebreaker), and per-class retest-only candidates - confluence is deliberately absent here,
 * unlike {@link WeeklyTrendBriefingReport}.
 *
 * @param regimePulses               W1 market-breadth snapshot per asset class; a missing/null value means no snapshot exists yet
 * @param btcW1State                 BTCUSDT's W1 trend state; null if BTCUSDT has no recorded state yet
 * @param btcD1State                 BTCUSDT's D1 trend state; null if BTCUSDT has no recorded state yet
 * @param btcW1Provisional           BTCUSDT's provisional W1 read; null if not enough data yet to compute one
 * @param bullishPullbackCandidates  W1 bullish, D1 recently flipped bearish (a dip within an uptrend), per asset class
 * @param bearishPullbackCandidates  W1 bearish, D1 recently flipped bullish (a bounce within a downtrend) - CRYPTO only, since stocks/ETFs are longs-only
 * @param bullishScanCandidates      liquidity-gated + ranked version of {@code bullishPullbackCandidates}
 * @param bearishScanCandidates      liquidity-gated + ranked version of {@code bearishPullbackCandidates} - CRYPTO only
 * @param divergingProvisional       symbol -&gt; provisional W1 read, only for candidates (in any of the four lists above) whose provisional read disagrees with the committed W1 direction that list is built on - per the divergence-only rule, a candidate not in this map has nothing new to report
 */
public record WeeklyPullbackBriefingReport(
        long ingestionRunId,
        IngestionRunStatus ingestionStatus,
        Map<AssetClass, MarketBreadthSnapshot> regimePulses,
        TrendState btcW1State,
        TrendState btcD1State,
        ProvisionalTrendResult btcW1Provisional,
        Map<AssetClass, List<SignalSummary>> bullishPullbackCandidates,
        Map<AssetClass, List<SignalSummary>> bearishPullbackCandidates,
        Map<AssetClass, List<ScanResult>> bullishScanCandidates,
        Map<AssetClass, List<ScanResult>> bearishScanCandidates,
        Map<String, ProvisionalTrendResult> divergingProvisional
) {
    public WeeklyPullbackBriefingReport {
        Objects.requireNonNull(ingestionStatus, "ingestionStatus must not be null");
        Objects.requireNonNull(regimePulses, "regimePulses must not be null");
        Objects.requireNonNull(bullishPullbackCandidates, "bullishPullbackCandidates must not be null");
        Objects.requireNonNull(bearishPullbackCandidates, "bearishPullbackCandidates must not be null");
        Objects.requireNonNull(bullishScanCandidates, "bullishScanCandidates must not be null");
        Objects.requireNonNull(bearishScanCandidates, "bearishScanCandidates must not be null");
        Objects.requireNonNull(divergingProvisional, "divergingProvisional must not be null");
    }

    public boolean btcAligned() {
        return btcW1State != null && btcW1State == btcD1State;
    }
}
