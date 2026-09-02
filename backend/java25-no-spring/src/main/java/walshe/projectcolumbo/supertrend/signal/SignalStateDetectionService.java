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
import walshe.projectcolumbo.supertrend.persistence.PersistenceException;
import walshe.projectcolumbo.supertrend.persistence.SignalStateDao;
import walshe.projectcolumbo.supertrend.pipeline.AssetComputationOutcome;
import walshe.projectcolumbo.supertrend.pipeline.ParallelAssetExecutor;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Detects trend state and reversal events for every active asset in a timeframe. Mirrors the
 * bounded incremental pattern in
 * {@link walshe.projectcolumbo.supertrend.indicator.IndicatorComputationService}: an asset with
 * no finalized candle newer than its last persisted signal-state row is skipped entirely, and an
 * asset that does have new candles recomputes SuperTrend over only a warm-up window before its
 * anchor (see {@link CandleDao#findWindowForIncremental}) rather than its full history. The
 * warm-up window is large enough that the trend state carried into the first post-anchor candle
 * is fully established, so a flip landing exactly on that candle is still detected. Only rows
 * after the last persisted close time are upserted. Per-asset work runs on its own virtual
 * thread (see {@link ParallelAssetExecutor}), sharing a single connection across all of that
 * asset's DB calls (see {@code IndicatorComputationService}'s Javadoc for why).
 */
public final class SignalStateDetectionService {

    private static final Logger LOG = LoggerFactory.getLogger(SignalStateDetectionService.class);

    private final AssetDao assetDao;
    private final CandleDao candleDao;
    private final SignalStateDao signalStateDao;
    private final DataSource dataSource;
    private final SuperTrendCalculator calculator = new SuperTrendCalculator();

    public SignalStateDetectionService(AssetDao assetDao, CandleDao candleDao, SignalStateDao signalStateDao, DataSource dataSource) {
        this.assetDao = Objects.requireNonNull(assetDao, "assetDao must not be null");
        this.candleDao = Objects.requireNonNull(candleDao, "candleDao must not be null");
        this.signalStateDao = Objects.requireNonNull(signalStateDao, "signalStateDao must not be null");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    public void computeForAllActiveAssets(Timeframe timeframe) {
        List<Asset> activeAssets = assetDao.findAllActive();
        LOG.info("Detecting {} signal state for {} active assets", timeframe, activeAssets.size());
        List<AssetComputationOutcome> outcomes = ParallelAssetExecutor.runForEachItem(
                activeAssets, asset -> computeForAssetSafely(asset, timeframe));
        long computed = outcomes.stream().filter(o -> o == AssetComputationOutcome.COMPUTED).count();
        long skipped = outcomes.stream().filter(o -> o == AssetComputationOutcome.SKIPPED).count();
        LOG.debug("{} signal state: computed {}, skipped {} (unchanged) of {} active assets",
                timeframe, computed, skipped, activeAssets.size());
    }

    private AssetComputationOutcome computeForAssetSafely(Asset asset, Timeframe timeframe) {
        try {
            return computeForAsset(asset, timeframe);
        } catch (Exception e) {
            LOG.error("Failed to detect {} signal state for asset {}", timeframe, asset.symbol(), e);
            return AssetComputationOutcome.FAILED;
        }
    }

    AssetComputationOutcome computeForAsset(Asset asset, Timeframe timeframe) {
        try (Connection connection = dataSource.getConnection()) {
            Optional<OffsetDateTime> lastStoredCloseTime = signalStateDao.findLatestCloseTime(connection, asset.id(), timeframe);
            Optional<OffsetDateTime> latestCandle = candleDao.findLatestCloseTime(connection, asset.id(), timeframe);

            if (latestCandle.isEmpty()) {
                return AssetComputationOutcome.NO_CANDLES;
            }
            if (lastStoredCloseTime.isPresent() && !latestCandle.get().isAfter(lastStoredCloseTime.get())) {
                return AssetComputationOutcome.SKIPPED;
            }

            List<Candle> candles = lastStoredCloseTime.isPresent()
                    ? candleDao.findWindowForIncremental(connection, asset.id(), timeframe, lastStoredCloseTime.get(), SuperTrendCalculator.WARMUP_WINDOW_BARS)
                    : candleDao.findByAssetAndTimeframe(connection, asset.id(), timeframe);
            if (candles.isEmpty()) {
                return AssetComputationOutcome.NO_CANDLES;
            }

            List<Optional<SuperTrendResult>> results = calculator.calculate(
                    candles, SuperTrendCalculator.DEFAULT_ATR_LENGTH, SuperTrendCalculator.DEFAULT_MULTIPLIER);
            List<SignalState> states = detect(asset.id(), timeframe, candles, results);

            for (SignalState state : states) {
                if (lastStoredCloseTime.isPresent() && !state.closeTime().isAfter(lastStoredCloseTime.get())) {
                    continue;
                }
                signalStateDao.upsert(connection, state);
            }
            return AssetComputationOutcome.COMPUTED;
        } catch (SQLException e) {
            throw new PersistenceException("Failed to acquire connection to detect signal state for asset " + asset.symbol(), e);
        }
    }

    /**
     * Pure: maps each candle to a trend state — {@code UNKNOWN} where {@code results} has no
     * value yet (still in ATR warm-up), {@code BULLISH}/{@code BEARISH} otherwise — and flags a
     * reversal only where the trend actually flips between {@code BULLISH} and {@code BEARISH}.
     * A transition into or out of {@code UNKNOWN}, or repeating the same state, is never a
     * reversal — there's nothing established yet to reverse from.
     *
     * @param candles ordered oldest-to-newest; {@code results} is the same-sized, same-order
     *                output of {@link SuperTrendCalculator#calculate}
     */
    static List<SignalState> detect(long assetId, Timeframe timeframe, List<Candle> candles, List<Optional<SuperTrendResult>> results) {
        List<SignalState> states = new ArrayList<>(candles.size());
        TrendState previous = null;
        for (int i = 0; i < candles.size(); i++) {
            TrendState current = results.get(i).map(SignalStateDetectionService::trendStateOf).orElse(TrendState.UNKNOWN);
            states.add(new SignalState(assetId, timeframe, candles.get(i).closeTime(), current, reversalEvent(previous, current)));
            previous = current;
        }
        return states;
    }

    private static TrendState trendStateOf(SuperTrendResult result) {
        return result.direction() == SuperTrendDirection.UP ? TrendState.BULLISH : TrendState.BEARISH;
    }

    private static SignalEvent reversalEvent(TrendState previous, TrendState current) {
        if (previous == TrendState.BULLISH && current == TrendState.BEARISH) {
            return SignalEvent.BEARISH_REVERSAL;
        }
        if (previous == TrendState.BEARISH && current == TrendState.BULLISH) {
            return SignalEvent.BULLISH_REVERSAL;
        }
        return SignalEvent.NONE;
    }
}
