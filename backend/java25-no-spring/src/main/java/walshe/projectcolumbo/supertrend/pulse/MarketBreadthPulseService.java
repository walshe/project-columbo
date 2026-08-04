package walshe.projectcolumbo.supertrend.pulse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import walshe.projectcolumbo.supertrend.persistence.Asset;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.MarketBreadthSnapshotDao;
import walshe.projectcolumbo.supertrend.persistence.SignalStateDao;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.signal.SignalState;
import walshe.projectcolumbo.supertrend.signal.TrendState;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Computes and persists a per-run market-breadth snapshot from the latest signal state of every
 * active asset, intended to run immediately after signal-state detection for a timeframe (see
 * the market-breadth-pulse spec). The snapshot's close time is the most recent close time among
 * those latest signal states — "as of the freshest data available" — rather than a wall-clock
 * boundary.
 */
public final class MarketBreadthPulseService {

    private static final Logger LOG = LoggerFactory.getLogger(MarketBreadthPulseService.class);
    private static final int BULLISH_RATIO_SCALE = 4;

    private final AssetDao assetDao;
    private final SignalStateDao signalStateDao;
    private final MarketBreadthSnapshotDao marketBreadthSnapshotDao;

    public MarketBreadthPulseService(AssetDao assetDao, SignalStateDao signalStateDao, MarketBreadthSnapshotDao marketBreadthSnapshotDao) {
        this.assetDao = Objects.requireNonNull(assetDao, "assetDao must not be null");
        this.signalStateDao = Objects.requireNonNull(signalStateDao, "signalStateDao must not be null");
        this.marketBreadthSnapshotDao = Objects.requireNonNull(marketBreadthSnapshotDao, "marketBreadthSnapshotDao must not be null");
    }

    public void computeForAllActiveAssets(Timeframe timeframe) {
        List<Asset> activeAssets = assetDao.findAllActive();
        Set<Long> activeAssetIds = activeAssets.stream().map(Asset::id).collect(Collectors.toSet());

        // findLatestForAllAssets isn't scoped to active assets (no join to asset table) - a
        // deactivated asset's last-known state would otherwise leak into the counts and break
        // the bullish+bearish+missing == totalAssets invariant.
        List<SignalState> activeLatestStates = signalStateDao.findLatestForAllAssets(timeframe).stream()
                .filter(state -> activeAssetIds.contains(state.assetId()))
                .toList();

        if (activeLatestStates.isEmpty()) {
            LOG.info("No signal state yet for active {} assets; skipping market breadth snapshot", timeframe);
            return;
        }

        OffsetDateTime snapshotCloseTime = activeLatestStates.stream()
                .map(SignalState::closeTime)
                .max(Comparator.naturalOrder())
                .orElseThrow();

        MarketBreadthSnapshot snapshot = snapshot(timeframe, snapshotCloseTime, activeLatestStates, activeAssets.size());
        marketBreadthSnapshotDao.upsert(snapshot);
        LOG.info("Market breadth snapshot for {} at {}: {}", timeframe, snapshotCloseTime, snapshot);
    }

    /** Pure: tallies trend states into bullish/bearish/missing counts and the bullish ratio. */
    static MarketBreadthSnapshot snapshot(Timeframe timeframe, OffsetDateTime snapshotCloseTime, List<SignalState> latestStates, int totalAssets) {
        Map<TrendState, Long> countsByTrendState = latestStates.stream()
                .collect(Collectors.groupingBy(SignalState::trendState, Collectors.counting()));
        int bullishCount = countsByTrendState.getOrDefault(TrendState.BULLISH, 0L).intValue();
        int bearishCount = countsByTrendState.getOrDefault(TrendState.BEARISH, 0L).intValue();
        int missingCount = totalAssets - bullishCount - bearishCount;

        return new MarketBreadthSnapshot(
                timeframe, snapshotCloseTime, bullishCount, bearishCount, missingCount, totalAssets, bullishRatio(bullishCount, bearishCount));
    }

    private static BigDecimal bullishRatio(int bullishCount, int bearishCount) {
        int directionalCount = bullishCount + bearishCount;
        if (directionalCount == 0) {
            return BigDecimal.ZERO.setScale(BULLISH_RATIO_SCALE);
        }
        return BigDecimal.valueOf(bullishCount).divide(BigDecimal.valueOf(directionalCount), BULLISH_RATIO_SCALE, RoundingMode.HALF_UP);
    }
}
