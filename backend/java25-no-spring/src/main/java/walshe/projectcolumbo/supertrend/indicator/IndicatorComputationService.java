package walshe.projectcolumbo.supertrend.indicator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import walshe.projectcolumbo.supertrend.persistence.Asset;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.persistence.SuperTrendIndicatorDao;
import walshe.projectcolumbo.supertrend.persistence.PersistenceException;
import walshe.projectcolumbo.supertrend.pipeline.ParallelAssetExecutor;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Computes and persists SuperTrend for every active asset in a timeframe. Per-asset work runs
 * on its own virtual thread (see {@link ParallelAssetExecutor}); one asset's failure is caught
 * and logged without affecting the others. All of one asset's DB calls share a single connection
 * (acquired once per asset, not once per statement) - with hundreds of assets computed
 * concurrently and, on a full backfill, potentially hundreds of new rows to upsert per asset,
 * acquiring a fresh pool connection for every individual call was a real contributor to HikariCP
 * pool exhaustion in production.
 */
public final class IndicatorComputationService {

    private static final Logger LOG = LoggerFactory.getLogger(IndicatorComputationService.class);

    private final AssetDao assetDao;
    private final CandleDao candleDao;
    private final SuperTrendIndicatorDao superTrendIndicatorDao;
    private final DataSource dataSource;
    private final SuperTrendCalculator calculator = new SuperTrendCalculator();

    public IndicatorComputationService(AssetDao assetDao, CandleDao candleDao, SuperTrendIndicatorDao superTrendIndicatorDao, DataSource dataSource) {
        this.assetDao = Objects.requireNonNull(assetDao, "assetDao must not be null");
        this.candleDao = Objects.requireNonNull(candleDao, "candleDao must not be null");
        this.superTrendIndicatorDao = Objects.requireNonNull(superTrendIndicatorDao, "superTrendIndicatorDao must not be null");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    public void computeForAllActiveAssets(Timeframe timeframe) {
        List<Asset> activeAssets = assetDao.findAllActive();
        LOG.info("Computing {} SuperTrend for {} active assets", timeframe, activeAssets.size());
        ParallelAssetExecutor.runForEachItem(activeAssets, asset -> {
            computeForAssetSafely(asset, timeframe);
            return null;
        });
    }

    private void computeForAssetSafely(Asset asset, Timeframe timeframe) {
        try {
            computeForAsset(asset, timeframe);
        } catch (Exception e) {
            LOG.error("Failed to compute {} SuperTrend for asset {}", timeframe, asset.symbol(), e);
        }
    }

    void computeForAsset(Asset asset, Timeframe timeframe) {
        try (Connection connection = dataSource.getConnection()) {
            List<Candle> candles = candleDao.findByAssetAndTimeframe(connection, asset.id(), timeframe);
            if (candles.isEmpty()) {
                return;
            }

            Optional<OffsetDateTime> lastStored = superTrendIndicatorDao.findLatestCloseTime(connection, asset.id(), timeframe);
            List<SuperTrendResult> results = calculator.calculateIncremental(
                    candles,
                    SuperTrendCalculator.DEFAULT_ATR_LENGTH,
                    SuperTrendCalculator.DEFAULT_MULTIPLIER,
                    lastStored.orElse(null),
                    false
            );

            for (SuperTrendResult result : results) {
                superTrendIndicatorDao.upsert(connection, asset.id(), timeframe, result);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to acquire connection to compute SuperTrend for asset " + asset.symbol(), e);
        }
    }
}
