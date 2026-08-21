package walshe.projectcolumbo.supertrend.rollup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.persistence.Asset;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.shared.FinalizedBoundary;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Derives weekly (W1) candles from finalized D1 candles, grouped into Monday-start weeks.
 * A W1 candle is only emitted once exactly 7 source D1 candles exist for that week and the
 * last of those 7 is itself finalized.
 */
public final class CandleRollupService {

    private static final Logger LOG = LoggerFactory.getLogger(CandleRollupService.class);
    private static final int CANDLES_PER_WEEK = 7;

    private final AssetDao assetDao;
    private final CandleDao candleDao;
    private final Clock clock;

    public CandleRollupService(AssetDao assetDao, CandleDao candleDao, Clock clock) {
        this.assetDao = Objects.requireNonNull(assetDao, "assetDao must not be null");
        this.candleDao = Objects.requireNonNull(candleDao, "candleDao must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void rollupForAllActiveAssets() {
        List<Asset> activeAssets = assetDao.findAllActive();
        LOG.info("Starting D1->W1 rollup for {} active assets", activeAssets.size());
        for (Asset asset : activeAssets) {
            try {
                rollupForAsset(asset);
            } catch (Exception e) {
                LOG.error("Failed to roll up candles for asset {}", asset.symbol(), e);
            }
        }
    }

    void rollupForAsset(Asset asset) {
        List<Candle> sourceCandles = candleDao.findByAssetAndTimeframe(asset.id(), Timeframe.D1);
        if (sourceCandles.isEmpty()) {
            return;
        }

        Optional<OffsetDateTime> lastStoredWeekClose = candleDao.findLatestCloseTime(asset.id(), Timeframe.W1);
        OffsetDateTime finalizedBoundary = FinalizedBoundary.utcMidnightToday(OffsetDateTime.now(clock));

        for (Map.Entry<OffsetDateTime, List<Candle>> entry : WeeklyCandleAggregation.groupByWeek(sourceCandles).entrySet()) {
            List<Candle> weekCandles = entry.getValue();
            if (weekCandles.size() != CANDLES_PER_WEEK) {
                continue;
            }

            Candle lastCandle = weekCandles.get(weekCandles.size() - 1);
            if (!lastCandle.closeTime().isBefore(finalizedBoundary)) {
                continue;
            }

            OffsetDateTime weekCloseTime = lastCandle.closeTime();
            if (lastStoredWeekClose.isPresent() && !weekCloseTime.isAfter(lastStoredWeekClose.get())) {
                continue;
            }

            candleDao.upsert(asset.id(), WeeklyCandleAggregation.aggregate(weekCandles));
        }
    }
}
