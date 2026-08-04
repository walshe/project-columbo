package walshe.projectcolumbo.supertrend.freshness;

import walshe.projectcolumbo.supertrend.persistence.CandleDao;
import walshe.projectcolumbo.supertrend.persistence.IngestionRunDao;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Evaluates whether a timeframe's candle data is up to date, and exposes the freshness metadata
 * every in-scope read API is expected to surface. Not yet wired into any HTTP handler (see
 * openspec tasks.md group 9) - {@link #requireFresh} is the seam a future {@code requireFresh}
 * query-param check will call.
 */
public final class FreshnessService {

    private static final Duration GRACE_WINDOW = Duration.ofHours(6);

    private final CandleDao candleDao;
    private final IngestionRunDao ingestionRunDao;
    private final Clock clock;

    public FreshnessService(CandleDao candleDao, IngestionRunDao ingestionRunDao, Clock clock) {
        this.candleDao = Objects.requireNonNull(candleDao, "candleDao must not be null");
        this.ingestionRunDao = Objects.requireNonNull(ingestionRunDao, "ingestionRunDao must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public FreshnessStatus evaluate(Timeframe timeframe) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime expectedLatestCloseTime = FreshnessBoundary.expectedLatestCloseTime(timeframe, now);
        OffsetDateTime actualLatestCloseTime = candleDao.findLatestCloseTimeAcrossAllAssets(timeframe).orElse(null);
        return evaluate(timeframe, expectedLatestCloseTime, actualLatestCloseTime, now);
    }

    public void requireFresh(Timeframe timeframe) {
        FreshnessStatus status = evaluate(timeframe);
        if (status.staleBeyondGraceWindow()) {
            throw new StaleDataException(status);
        }
    }

    public FreshnessMetadata metadataFor(Provider provider, Timeframe timeframe) {
        return metadataFor(provider, evaluate(timeframe));
    }

    /** Reuses an already-computed status's candle lookup instead of re-querying it - see {@link #metadataFor(Provider, Timeframe)}. */
    public FreshnessMetadata metadataFor(Provider provider, FreshnessStatus status) {
        OffsetDateTime lastSuccessfulIngestionAt = ingestionRunDao.findLatestSuccessfulFinishedAt(provider, status.timeframe()).orElse(null);
        return new FreshnessMetadata(lastSuccessfulIngestionAt, status.actualLatestCloseTime());
    }

    /**
     * Pure: up-to-date and grace-window comparison, testable without a database. Grace is
     * measured from wall-clock time elapsed since the expected boundary (not from the size of
     * the data gap) - it exists to tolerate the normal lag between "candle technically closed"
     * and "today's pipeline run finished ingesting it", not to permanently excuse a large gap
     * once the pipeline does catch up.
     */
    static FreshnessStatus evaluate(
            Timeframe timeframe, OffsetDateTime expectedLatestCloseTime, OffsetDateTime actualLatestCloseTime, OffsetDateTime now) {
        boolean upToDate = actualLatestCloseTime != null && !actualLatestCloseTime.isBefore(expectedLatestCloseTime);
        boolean staleBeyondGraceWindow = !upToDate && Duration.between(expectedLatestCloseTime, now).compareTo(GRACE_WINDOW) > 0;
        return new FreshnessStatus(timeframe, expectedLatestCloseTime, actualLatestCloseTime, upToDate, staleBeyondGraceWindow);
    }
}
