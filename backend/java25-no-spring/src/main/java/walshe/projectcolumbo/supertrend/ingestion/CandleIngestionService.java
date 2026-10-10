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
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Ingests D1 candles for every active asset: computes an incremental time window per asset,
 * fetches from the provider for that asset's {@link AssetVenue} - paginating within a single run
 * until the asset is caught up to now or the provider stops returning data - persists
 * idempotently, and isolates one asset's failure from the rest of the run (a provider error or
 * invalid symbol never aborts the whole run).
 */
public final class CandleIngestionService {

    private static final Logger LOG = LoggerFactory.getLogger(CandleIngestionService.class);
    private static final long POLITE_DELAY_MS = 200;
    // Every klines provider we use (Binance, MEXC) caps a single request's row count well short of a
    // multi-year backfill (~500 candles by default) - this bounds how many follow-up calls one
    // asset's catch-up can make in a single run, so a misbehaving response can never loop forever.
    // 20 iterations comfortably covers a multi-year backfill at that page size; if it's ever not
    // enough, the remaining history is picked up on the next run anyway.
    static final int MAX_FETCH_ITERATIONS_PER_ASSET = 20;
    private static final long ONE_DAY_MS = Duration.ofDays(1).toMillis();

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
        Optional<OffsetDateTime> lastOpen = candleDao.findLatestOpenTime(asset.id(), Timeframe.D1);

        // Resume from the last stored bar's OPEN time + 1ms: every provider returns bars with
        // open >= startTime, so the next bar (opening a full day later) is always included,
        // whatever the provider's close-time convention. Resuming from closeTime + 1 skipped a day
        // per run on MEXC, whose raw close time is the next bar's open.
        long startTimeMs;
        Long previousOpenMs = null;
        if (lastOpen.isPresent()) {
            previousOpenMs = lastOpen.get().toInstant().toEpochMilli();
            startTimeMs = previousOpenMs + 1;
        } else {
            OffsetDateTime backfillStart = ingestionConfig.backfillStart();
            if (backfillStart == null) {
                throw new IllegalStateException(
                        "SUPERTREND_BACKFILL_START is not configured; cannot backfill asset " + asset.symbol());
            }
            startTimeMs = backfillStart.toInstant().toEpochMilli();
        }
        long endTimeMs = finalizedBoundary.toInstant().toEpochMilli();

        // Caught up when the next bar would open at/after the finalized boundary (it isn't closed yet).
        if (startTimeMs >= endTimeMs || (previousOpenMs != null && previousOpenMs + ONE_DAY_MS >= endTimeMs)) {
            LOG.debug("No new candles required for {}. Skipping.", asset.symbol());
            return IngestionStats.EMPTY;
        }

        MarketDataProvider provider = providersByVenue.get(asset.venue());
        if (provider == null) {
            throw new IllegalStateException("No market data provider configured for venue " + asset.venue());
        }

        return fetchAndUpsertUntilCaughtUp(asset, provider, startTimeMs, endTimeMs, previousOpenMs);
    }

    /**
     * Repeatedly fetches from wherever the previous page left off until the asset reaches {@code
     * endTimeMs} or a fetch comes back empty - nothing more available right now (e.g. the asset's
     * real history hasn't started yet, or a transient gap); the next scheduled/triggered run will
     * retry the same remaining window. Without this loop, an asset whose full requested range
     * exceeds what a single provider call returns (a brand-new asset with years of real history,
     * or one that's fallen behind) would only advance by one page per ingestion run instead of
     * catching all the way up to now in one run.
     *
     * @param previousOpenMs open time of the latest already-stored bar, or null on a fresh
     *                       backfill - seeds gap detection across the stored/fetched boundary
     */
    private IngestionStats fetchAndUpsertUntilCaughtUp(Asset asset, MarketDataProvider provider, long startTimeMs, long endTimeMs,
                                                       Long previousOpenMs) {
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        long windowStart = startTimeMs;

        for (int iteration = 0; windowStart < endTimeMs; iteration++) {
            if (iteration >= MAX_FETCH_ITERATIONS_PER_ASSET) {
                LOG.warn("Asset {} hit the {}-iteration catch-up cap for this run; remaining history will be picked up next run.",
                        asset.symbol(), MAX_FETCH_ITERATIONS_PER_ASSET);
                break;
            }

            List<Candle> candles = provider.fetchDailyCandles(asset.symbol(), windowStart, endTimeMs);
            if (candles.isEmpty()) {
                if (iteration == 0) {
                    LOG.warn("Provider returned zero candles for {} in an expected fetch window ({} to {}); "
                            + "no error was raised, but this is not the same as already being up to date.",
                            asset.symbol(), windowStart, endTimeMs);
                }
                break;
            }

            for (Candle candle : candles) {
                long openMs = candle.openTime().toInstant().toEpochMilli();
                if (previousOpenMs != null && openMs - previousOpenMs > ONE_DAY_MS) {
                    // Logged, not failed: genuine gaps exist (halts, delistings) and failing the asset would block all later data.
                    LOG.warn("Gap in D1 candles for {}: no bar between {} and {}",
                            asset.symbol(), Instant.ofEpochMilli(previousOpenMs), candle.openTime());
                }
                previousOpenMs = openMs;
                switch (candleDao.upsert(asset.id(), candle)) {
                    case INSERTED -> inserted++;
                    case UPDATED -> updated++;
                    case UNCHANGED -> unchanged++;
                }
            }

            long lastOpenMs = candles.get(candles.size() - 1).openTime().toInstant().toEpochMilli();
            if (lastOpenMs + ONE_DAY_MS >= endTimeMs) {
                break; // caught up: the next bar wouldn't be finalized yet, so don't spend a call confirming it
            }
            long nextWindowStart = lastOpenMs + 1;
            if (nextWindowStart <= windowStart) {
                // Defensive: a provider response that doesn't advance the window would otherwise loop forever.
                break;
            }
            windowStart = nextWindowStart;

            if (windowStart < endTimeMs) {
                politeDelay();
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
