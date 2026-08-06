package walshe.projectcolumbo.supertrend.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import walshe.projectcolumbo.supertrend.indicator.Candle;
import walshe.projectcolumbo.supertrend.persistence.Asset;
import walshe.projectcolumbo.supertrend.persistence.AssetDao;
import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.shared.AssetVenue;
import walshe.projectcolumbo.supertrend.shared.FinalizedBoundary;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Ingests D1 candles for every active asset: computes an incremental time window per asset,
 * fetches from the provider for that asset's {@link AssetVenue}, persists idempotently, and
 * isolates one asset's failure from the rest of the run (a provider error or invalid symbol
 * never aborts the whole run).
 */
public final class CandleIngestionService {

    private static final Logger LOG = LoggerFactory.getLogger(CandleIngestionService.class);
    private static final long POLITE_DELAY_MS = 200;

    private final AssetDao assetDao;
    private final CandleDao candleDao;
    private final Map<AssetVenue, MarketDataProvider> providersByVenue;
    private final IngestionConfig ingestionConfig;
    private final Clock clock;

    /** @param providersByVenue SHALL have an entry for every {@link AssetVenue}. */
    public CandleIngestionService(
            AssetDao assetDao,
            CandleDao candleDao,
            Map<AssetVenue, MarketDataProvider> providersByVenue,
            IngestionConfig ingestionConfig,
            Clock clock
    ) {
        this.assetDao = Objects.requireNonNull(assetDao, "assetDao must not be null");
        this.candleDao = Objects.requireNonNull(candleDao, "candleDao must not be null");
        this.providersByVenue = Map.copyOf(Objects.requireNonNull(providersByVenue, "providersByVenue must not be null"));
        this.ingestionConfig = Objects.requireNonNull(ingestionConfig, "ingestionConfig must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public IngestionStats ingestDaily() {
        List<Asset> activeAssets = assetDao.findAllActive();
        LOG.info("Starting daily ingestion for {} active assets", activeAssets.size());

        IngestionStats total = IngestionStats.EMPTY;
        for (int i = 0; i < activeAssets.size(); i++) {
            total = total.plus(ingestForAssetSafely(activeAssets.get(i)));
            if (i < activeAssets.size() - 1) {
                politeDelay();
            }
        }

        LOG.info("Daily ingestion summary: {} inserted, {} updated, {} unchanged, {} errors across {} assets",
                total.insertedCount(), total.updatedCount(), total.unchangedCount(), total.errorCount(), activeAssets.size());
        return total;
    }

    private IngestionStats ingestForAssetSafely(Asset asset) {
        try {
            return ingestForAsset(asset);
        } catch (InvalidSymbolException e) {
            LOG.error("Invalid symbol for asset {}: deactivating.", asset.symbol());
            assetDao.deactivate(asset.id());
            return IngestionStats.singleError("Invalid symbol: " + asset.symbol());
        } catch (Exception e) {
            LOG.error("Failed to ingest data for asset: {}", asset.symbol(), e);
            return IngestionStats.singleError(e.getMessage());
        }
    }

    IngestionStats ingestForAsset(Asset asset) {
        OffsetDateTime finalizedBoundary = FinalizedBoundary.utcMidnightToday(OffsetDateTime.now(clock));
        Optional<OffsetDateTime> lastClose = candleDao.findLatestCloseTime(asset.id(), Timeframe.D1);

        long startTimeMs;
        if (lastClose.isPresent()) {
            startTimeMs = lastClose.get().toInstant().toEpochMilli() + 1;
        } else {
            OffsetDateTime backfillStart = ingestionConfig.backfillStart();
            if (backfillStart == null) {
                throw new IllegalStateException(
                        "SUPERTREND_BACKFILL_START is not configured; cannot backfill asset " + asset.symbol());
            }
            startTimeMs = backfillStart.toInstant().toEpochMilli();
        }
        long endTimeMs = finalizedBoundary.toInstant().toEpochMilli();

        if (startTimeMs >= endTimeMs) {
            LOG.debug("No new candles required for {}. Skipping.", asset.symbol());
            return IngestionStats.EMPTY;
        }

        MarketDataProvider provider = providersByVenue.get(asset.venue());
        if (provider == null) {
            throw new IllegalStateException("No market data provider configured for venue " + asset.venue());
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
