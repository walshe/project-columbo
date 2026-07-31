package walshe.projectcolumbo.supertrend.ingestion;

import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.persistence.Asset;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.shared.FinalizedBoundary;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Ingests D1 candles for every active asset: computes an incremental time window per asset,
 * fetches from the provider, persists idempotently, and isolates one asset's failure from the
 * rest of the run (a provider error or invalid symbol never aborts the whole run).
 */
public final class CandleIngestionService {

    private static final Logger LOG = System.getLogger(CandleIngestionService.class.getName());
    private static final long POLITE_DELAY_MS = 200;

    private final AssetDao assetDao;
    private final CandleDao candleDao;
    private final MarketDataProvider provider;
    private final IngestionConfig ingestionConfig;
    private final Clock clock;

    public CandleIngestionService(
            AssetDao assetDao,
            CandleDao candleDao,
            MarketDataProvider provider,
            IngestionConfig ingestionConfig,
            Clock clock
    ) {
        this.assetDao = assetDao;
        this.candleDao = candleDao;
        this.provider = provider;
        this.ingestionConfig = ingestionConfig;
        this.clock = clock;
    }

    public IngestionStats ingestDaily() {
        List<Asset> activeAssets = assetDao.findAllActive();
        LOG.log(Level.INFO, "Starting daily ingestion for {0} active assets", activeAssets.size());

        IngestionStats total = IngestionStats.EMPTY;
        for (int i = 0; i < activeAssets.size(); i++) {
            total = total.plus(ingestForAssetSafely(activeAssets.get(i)));
            if (i < activeAssets.size() - 1) {
                politeDelay();
            }
        }

        LOG.log(Level.INFO, "Daily ingestion summary: {0} inserted, {1} updated, {2} unchanged, {3} errors across {4} assets",
                total.insertedCount(), total.updatedCount(), total.unchangedCount(), total.errorCount(), activeAssets.size());
        return total;
    }

    private IngestionStats ingestForAssetSafely(Asset asset) {
        try {
            return ingestForAsset(asset);
        } catch (InvalidSymbolException e) {
            LOG.log(Level.ERROR, "Invalid symbol for asset {0}: deactivating.", asset.symbol());
            assetDao.deactivate(asset.id());
            return IngestionStats.singleError("Invalid symbol: " + asset.symbol());
        } catch (Exception e) {
            LOG.log(Level.ERROR, "Failed to ingest data for asset: " + asset.symbol(), e);
            return IngestionStats.singleError(e.getMessage());
        }
    }

    IngestionStats ingestForAsset(Asset asset) {
        OffsetDateTime finalizedBoundary = FinalizedBoundary.utcMidnightToday(OffsetDateTime.now(clock));
        Optional<OffsetDateTime> lastClose = candleDao.findLatestCloseTime(asset.id(), Timeframe.D1);

        long startTimeMs = lastClose
                .map(t -> t.toInstant().toEpochMilli() + 1)
                .orElseGet(() -> ingestionConfig.backfillStart().toInstant().toEpochMilli());
        long endTimeMs = finalizedBoundary.toInstant().toEpochMilli();

        if (startTimeMs >= endTimeMs) {
            LOG.log(Level.DEBUG, "No new candles required for {0}. Skipping.", asset.symbol());
            return IngestionStats.EMPTY;
        }

        List<Candle> candles = provider.fetchDailyCandles(asset.symbol(), startTimeMs, endTimeMs);

        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        for (Candle candle : candles) {
            switch (candleDao.upsert(asset.id(), candle)) {
                case INSERTED -> inserted++;
                case UPDATED -> updated++;
                case UNCHANGED -> unchanged++;
            }
        }
        return new IngestionStats(inserted, updated, unchanged, 0, null);
    }

    private void politeDelay() {
        try {
            Thread.sleep(POLITE_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
