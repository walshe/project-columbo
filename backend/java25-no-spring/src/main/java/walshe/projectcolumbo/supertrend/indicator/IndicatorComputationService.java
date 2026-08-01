package walshe.projectcolumbo.supertrend.indicator;

import walshe.projectcolumbo.supertrend.persistence.Asset;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.persistence.SuperTrendIndicatorDao;
import walshe.projectcolumbo.supertrend.pipeline.ParallelAssetExecutor;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Computes and persists SuperTrend for every active asset in a timeframe. Per-asset work runs
 * on its own virtual thread (see {@link ParallelAssetExecutor}); one asset's failure is caught
 * and logged without affecting the others.
 */
public final class IndicatorComputationService {

    private static final Logger LOG = System.getLogger(IndicatorComputationService.class.getName());

    private final AssetDao assetDao;
    private final CandleDao candleDao;
    private final SuperTrendIndicatorDao superTrendIndicatorDao;
    private final SuperTrendCalculator calculator = new SuperTrendCalculator();

    public IndicatorComputationService(AssetDao assetDao, CandleDao candleDao, SuperTrendIndicatorDao superTrendIndicatorDao) {
        this.assetDao = assetDao;
        this.candleDao = candleDao;
        this.superTrendIndicatorDao = superTrendIndicatorDao;
    }

    public void computeForAllActiveAssets(Timeframe timeframe) {
        List<Asset> activeAssets = assetDao.findAllActive();
        LOG.log(Level.INFO, "Computing {0} SuperTrend for {1} active assets", timeframe, activeAssets.size());
        ParallelAssetExecutor.runForEachItem(activeAssets, asset -> {
            computeForAssetSafely(asset, timeframe);
            return null;
        });
    }

    private void computeForAssetSafely(Asset asset, Timeframe timeframe) {
        try {
            computeForAsset(asset, timeframe);
        } catch (Exception e) {
            LOG.log(Level.ERROR, "Failed to compute " + timeframe + " SuperTrend for asset " + asset.symbol(), e);
        }
    }

    void computeForAsset(Asset asset, Timeframe timeframe) {
        List<Candle> candles = candleDao.findByAssetAndTimeframe(asset.id(), timeframe);
        if (candles.isEmpty()) {
            return;
        }

        Optional<OffsetDateTime> lastStored = superTrendIndicatorDao.findLatestCloseTime(asset.id(), timeframe);
        List<SuperTrendResult> results = calculator.calculateIncremental(
                candles,
                SuperTrendCalculator.DEFAULT_ATR_LENGTH,
                SuperTrendCalculator.DEFAULT_MULTIPLIER,
                lastStored.orElse(null),
                false
        );

        for (SuperTrendResult result : results) {
            superTrendIndicatorDao.upsert(asset.id(), timeframe, result);
        }
    }
}
