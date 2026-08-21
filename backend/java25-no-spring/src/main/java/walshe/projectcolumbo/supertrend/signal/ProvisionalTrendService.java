package walshe.projectcolumbo.supertrend.signal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.indicator.SuperTrendCalculator;
import walshe.projectcolumbo.supertrend.indicator.SuperTrendDirection;
import walshe.projectcolumbo.supertrend.indicator.SuperTrendResult;
import walshe.projectcolumbo.supertrend.persistence.Asset;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.pipeline.ParallelAssetExecutor;
import walshe.projectcolumbo.supertrend.rollup.WeeklyCandleAggregation;
import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Computes a provisional W1 trend read per asset - "if the current week closed right now, based
 * on the latest ingested D1 close, would this asset's weekly trend flip, and at what price?" -
 * entirely in-memory, recomputed fresh on every call. Never persisted: no write to
 * {@code signal_state}, {@code signal_event}, or {@code candles} results from calling this. W1
 * only - see {@code openspec/changes/provisional-w1-flip-detection/design.md} for why D1/intraday
 * provisional detection is explicitly out of scope (it would need genuinely new intraday
 * ingestion, not just a different computation).
 * <p>
 * Reuses two existing pure pieces rather than inventing new math: {@link WeeklyCandleAggregation}
 * to synthesize a week-to-date candle from this week's D1 candles so far, and
 * {@link SuperTrendCalculator#calculateIncremental} (already used by
 * {@link walshe.projectcolumbo.supertrend.indicator.IndicatorComputationService}) to get just
 * that synthetic candle's result appended after the asset's committed W1 history.
 */
public final class ProvisionalTrendService {

    private static final Logger LOG = LoggerFactory.getLogger(ProvisionalTrendService.class);

    private final AssetDao assetDao;
    private final CandleDao candleDao;
    private final Clock clock;
    private final SuperTrendCalculator calculator = new SuperTrendCalculator();

    public ProvisionalTrendService(AssetDao assetDao, CandleDao candleDao, Clock clock) {
        this.assetDao = Objects.requireNonNull(assetDao, "assetDao must not be null");
        this.candleDao = Objects.requireNonNull(candleDao, "candleDao must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * @return one entry per active asset in {@code assetClassFilter} with enough this-week D1
     * data to synthesize a provisional read and enough committed W1 history to be past ATR
     * warm-up; assets with neither yet are omitted rather than erroring, keyed by symbol
     */
    public Map<String, ProvisionalTrendResult> computeForActiveAssets(AssetClass assetClassFilter) {
        List<Asset> activeAssets = assetDao.findAllActive(assetClassFilter);
        List<Map.Entry<String, ProvisionalTrendResult>> entries = ParallelAssetExecutor.runForEachItem(
                activeAssets, this::computeForAssetSafely);

        Map<String, ProvisionalTrendResult> results = new LinkedHashMap<>();
        for (Map.Entry<String, ProvisionalTrendResult> entry : entries) {
            if (entry != null) {
                results.put(entry.getKey(), entry.getValue());
            }
        }
        return results;
    }

    private Map.Entry<String, ProvisionalTrendResult> computeForAssetSafely(Asset asset) {
        try {
            return computeForAsset(asset);
        } catch (Exception e) {
            LOG.error("Failed to compute provisional W1 trend for asset {}", asset.symbol(), e);
            return null;
        }
    }

    private Map.Entry<String, ProvisionalTrendResult> computeForAsset(Asset asset) {
        List<Candle> weekToDateD1 = weekToDateD1Candles(asset.id());
        if (weekToDateD1.isEmpty()) {
            return null;
        }

        List<Candle> committedW1 = candleDao.findByAssetAndTimeframe(asset.id(), Timeframe.W1);
        return compute(calculator, committedW1, weekToDateD1)
                .map(result -> Map.entry(asset.symbol(), result))
                .orElse(null);
    }

    /**
     * Pure: synthesizes the week-to-date candle, appends it after the committed W1 history, and
     * runs the existing incremental SuperTrend calculation - testable without a database.
     * Empty when there's not yet enough committed history to be past ATR warm-up.
     *
     * @param committedW1 the asset's full committed W1 candle history, oldest-to-newest
     * @param weekToDateD1 this week's D1 candles ingested so far (Monday through today), oldest-to-newest
     */
    static Optional<ProvisionalTrendResult> compute(SuperTrendCalculator calculator, List<Candle> committedW1, List<Candle> weekToDateD1) {
        Candle provisionalCandle = WeeklyCandleAggregation.aggregate(weekToDateD1);

        List<Candle> withProvisional = new ArrayList<>(committedW1);
        withProvisional.add(provisionalCandle);

        OffsetDateTime lastCommittedCloseTime = committedW1.isEmpty() ? null : committedW1.get(committedW1.size() - 1).closeTime();
        List<SuperTrendResult> results = calculator.calculateIncremental(
                withProvisional, SuperTrendCalculator.DEFAULT_ATR_LENGTH, SuperTrendCalculator.DEFAULT_MULTIPLIER,
                lastCommittedCloseTime, false);
        if (results.isEmpty()) {
            return Optional.empty();
        }

        SuperTrendResult provisional = results.get(results.size() - 1);
        TrendState direction = provisional.direction() == SuperTrendDirection.UP ? TrendState.BULLISH : TrendState.BEARISH;
        return Optional.of(new ProvisionalTrendResult(direction, provisional.superTrend()));
    }

    private List<Candle> weekToDateD1Candles(long assetId) {
        List<Candle> allD1 = candleDao.findByAssetAndTimeframe(assetId, Timeframe.D1);
        OffsetDateTime currentWeekMonday = currentWeekMonday();
        OffsetDateTime nextWeekMonday = currentWeekMonday.plusWeeks(1);
        return allD1.stream()
                .filter(candle -> !candle.openTime().isBefore(currentWeekMonday) && candle.openTime().isBefore(nextWeekMonday))
                .toList();
    }

    private OffsetDateTime currentWeekMonday() {
        return OffsetDateTime.now(clock)
                .withOffsetSameInstant(ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .truncatedTo(ChronoUnit.DAYS);
    }
}
