package walshe.projectcolumbo.supertrend.signal;

import walshe.projectcolumbo.supertrend.persistence.Asset;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.AssetLiquidityDao;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.persistence.SignalStateDao;
import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.Timeframe;
import walshe.projectcolumbo.supertrend.shared.TradingViewUrl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read model backing {@code /api/v1/signals} and {@code /api/v1/assets/by-state}: the latest
 * signal state per active asset, enriched with 7-day average volume and percentage change since
 * the asset's last flip, filtered by trend state and sorted per {@link SignalSort}.
 */
public final class SignalQueryService {

    private final AssetDao assetDao;
    private final SignalStateDao signalStateDao;
    private final CandleDao candleDao;
    private final AssetLiquidityDao assetLiquidityDao;

    public SignalQueryService(AssetDao assetDao, SignalStateDao signalStateDao, CandleDao candleDao, AssetLiquidityDao assetLiquidityDao) {
        this.assetDao = Objects.requireNonNull(assetDao, "assetDao must not be null");
        this.signalStateDao = Objects.requireNonNull(signalStateDao, "signalStateDao must not be null");
        this.candleDao = Objects.requireNonNull(candleDao, "candleDao must not be null");
        this.assetLiquidityDao = Objects.requireNonNull(assetLiquidityDao, "assetLiquidityDao must not be null");
    }

    /** {@code sort} defaults to {@link SignalSort#ASSET_ASC} when null. {@code stateFilter} and {@code assetClassFilter} are skipped when null. */
    public List<SignalSummary> listSignals(Timeframe timeframe, TrendState stateFilter, SignalSort sort, AssetClass assetClassFilter) {
        Map<Long, Asset> activeAssetsById = assetDao.findAllActive(assetClassFilter).stream()
                .collect(Collectors.toMap(Asset::id, Function.identity()));

        // Neither DAO query is scoped to active assets (no join to asset table) - a deactivated
        // asset's last-known state would otherwise leak into the results.
        List<SignalState> latestStates = signalStateDao.findLatestForAllAssets(timeframe).stream()
                .filter(state -> activeAssetsById.containsKey(state.assetId()))
                .toList();
        Map<Long, SignalState> flipsByAssetId = signalStateDao.findLatestFlipsForAllAssets(timeframe).stream()
                .filter(state -> activeAssetsById.containsKey(state.assetId()))
                .collect(Collectors.toMap(SignalState::assetId, Function.identity()));

        Map<Long, BigDecimal> avgVolume7dByAssetId = assetLiquidityDao.findAvgVolume7d();
        Map<Long, BigDecimal> latestCloseByAssetId = candleDao.findLatestCloseByAssetForTimeframe(timeframe);
        Map<Long, OffsetDateTime> flipCloseTimeByAssetId = flipsByAssetId.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().closeTime()));
        Map<Long, BigDecimal> flipCloseByAssetId = candleDao.findCloseAtTimes(timeframe, flipCloseTimeByAssetId);

        Enrichment enrichment = new Enrichment(activeAssetsById, flipsByAssetId, avgVolume7dByAssetId, latestCloseByAssetId, flipCloseByAssetId);
        return summarize(latestStates, enrichment, stateFilter, sort);
    }

    /** The per-asset lookups {@link #summarize} enriches each latest {@link SignalState} with. */
    record Enrichment(
            Map<Long, Asset> assetsById,
            Map<Long, SignalState> flipsByAssetId,
            Map<Long, BigDecimal> avgVolume7dByAssetId,
            Map<Long, BigDecimal> latestCloseByAssetId,
            Map<Long, BigDecimal> flipCloseByAssetId) {
    }

    /** Pure: enrichment, filtering, and sorting, testable without a database. */
    static List<SignalSummary> summarize(List<SignalState> latestStates, Enrichment enrichment, TrendState stateFilter, SignalSort sort) {
        List<SignalSummary> summaries = new ArrayList<>();
        for (SignalState latest : latestStates) {
            if (stateFilter != null && latest.trendState() != stateFilter) {
                continue;
            }
            Asset asset = enrichment.assetsById().get(latest.assetId());
            SignalState flip = enrichment.flipsByAssetId().get(latest.assetId());
            BigDecimal avgVolume7d = enrichment.avgVolume7dByAssetId().getOrDefault(latest.assetId(), BigDecimal.ZERO);
            BigDecimal latestClose = enrichment.latestCloseByAssetId().get(latest.assetId());
            BigDecimal flipClose = flip != null ? enrichment.flipCloseByAssetId().get(latest.assetId()) : null;
            summaries.add(toSummary(asset, latest, flip, avgVolume7d, latestClose, flipClose));
        }
        summaries.sort(comparatorFor(sort));
        return summaries;
    }

    private static SignalSummary toSummary(
            Asset asset, SignalState latest, SignalState flip, BigDecimal avgVolume7d, BigDecimal latestClose, BigDecimal flipClose) {
        // Reported as the flip candle's open time, not its close time: charting tools like
        // TradingView position/label a candle at its open, so this keeps "days since flip"
        // consistent with where a human would see the flip marker land on a chart.
        OffsetDateTime lastFlipTime = flip != null ? flip.timeframe().openTimeFor(flip.closeTime()) : null;
        BigDecimal pctChangeSinceFlip = pctChangeSinceFlip(flipClose, latestClose);
        String tradingviewUrl = TradingViewUrl.generateUrl(asset.provider(), asset.symbol(), latest.timeframe());
        return new SignalSummary(asset.symbol(), latest.trendState(), lastFlipTime, avgVolume7d, pctChangeSinceFlip, tradingviewUrl, asset.assetClass());
    }

    private static BigDecimal pctChangeSinceFlip(BigDecimal flipClose, BigDecimal latestClose) {
        if (flipClose == null || latestClose == null || flipClose.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return latestClose.subtract(flipClose)
                .multiply(BigDecimal.valueOf(100))
                .divide(flipClose, 2, RoundingMode.HALF_UP);
    }

    private static Comparator<SignalSummary> comparatorFor(SignalSort sort) {
        SignalSort effective = sort != null ? sort : SignalSort.ASSET_ASC;
        return switch (effective) {
            case ASSET_ASC -> Comparator.comparing(SignalSummary::symbol);
            case LAST_FLIP_ASC -> Comparator.comparing(SignalSummary::lastFlipTime, Comparator.nullsLast(Comparator.naturalOrder()));
            case LAST_FLIP_DESC -> Comparator.comparing(SignalSummary::lastFlipTime, Comparator.nullsLast(Comparator.reverseOrder()));
            case TREND_STATE_ASC -> Comparator.comparing(SignalSummary::trendState);
            // avgVolume7d is never null (toSummary always defaults it to ZERO), so no nullsLast needed here.
            case LIQUIDITY_DESC -> Comparator.comparing(SignalSummary::avgVolume7d).reversed();
            case PCT_CHANGE_ASC -> Comparator.comparing(SignalSummary::pctChangeSinceFlip, Comparator.nullsLast(Comparator.naturalOrder()));
            case PCT_CHANGE_DESC -> Comparator.comparing(SignalSummary::pctChangeSinceFlip, Comparator.nullsLast(Comparator.reverseOrder()));
        };
    }
}
